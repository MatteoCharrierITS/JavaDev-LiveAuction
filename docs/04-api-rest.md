# Contratti REST e WebSocket

## Convenzioni

- Base REST: `http://localhost:8081/api/v1`.
- Date persistite e restituite in UTC ISO 8601.
- L'input amministrativo `LocalDateTime` è interpretato in `Europe/Rome`.
- Crediti con `BigDecimal` e due decimali.
- Errori REST in `application/problem+json`.
- Operazioni protette con bearer token gestito dalla Consumer.
- Gli endpoint `/admin/**` richiedono il ruolo `ADMIN`.

## REST

| Metodo | Endpoint | Accesso | Scopo |
| --- | --- | --- | --- |
| `POST` | `/auth/register` | pubblico | registra utente e wallet |
| `POST` | `/auth/login` | pubblico | apre una sessione e restituisce i token |
| `POST` | `/auth/refresh` | pubblico | rinnova i token usando il refresh token |
| `POST` | `/auth/logout` | Bearer | revoca la sessione corrente |
| `DELETE` | `/me` | Bearer | disattiva e anonimizza il proprio account |
| `GET` | `/categorie` | pubblico | categorie attive |
| `GET` | `/admin/categorie` | ADMIN | tutte le categorie |
| `POST` | `/admin/categorie` | ADMIN | crea categoria |
| `PUT` | `/admin/categorie/{id}` | ADMIN | modifica categoria |
| `GET` | `/admin/prodotti` | ADMIN | catalogo completo con filtri |
| `GET` | `/admin/prodotti/{id}` | ADMIN | dettaglio anche non pubblico |
| `POST` | `/admin/prodotti` | ADMIN | crea prodotto |
| `GET` | `/prodotti` | pubblico | catalogo paginato |
| `GET` | `/prodotti/{id}` | pubblico | dettaglio, stock e `astabile` |
| `POST` | `/prodotti/{id}/acquisti` | USER | acquisto fisso |
| `GET` | `/me/inventario` | USER | prodotti posseduti |
| `GET` | `/me/portafoglio` | USER | saldo e movimenti |
| `PUT` | `/me/portafoglio/impostazioni` | USER | imposta saldo finto |
| `GET` | `/me/vittorie` | USER | storico personale vittorie |
| `GET` | `/aste` | pubblico | lobby filtrabile |
| `GET` | `/aste/{id}` | pubblico | snapshot autorevole |
| `POST` | `/aste/{id}/ticket` | USER | ticket WebSocket breve |
| `POST` | `/admin/aste` | ADMIN | programma asta e blocca stock |
| `POST` | `/admin/aste/{id}/annullamento` | ADMIN | annulla quando consentito |
| `GET` | `/admin/aste/storico` | ADMIN | storico globale e vincitori |
| `PUT` | `/admin/prodotti/{id}` | ADMIN | modifica prodotto, stock e flag |

## Autenticazione

### Regole di accesso nel Producer

Il Producer valida il JWT e usa il suo `sub` come ID utente. A ogni richiesta
protetta controlla che la sessione sia valida e che l'utente sia ancora attivo;
legge poi il ruolo corrente dal database. `Ruolo.USER` e `Ruolo.ADMIN` diventano
rispettivamente le autorità Spring `ROLE_USER` e `ROLE_ADMIN`. Perciò
`hasRole('ADMIN')` verifica `ROLE_ADMIN`, senza affidarsi a un ruolo nel JWT.

Le regole HTTP di `SecurityConfig` rendono pubblici solo registrazione, login,
refresh, health, `GET /categorie` e i `GET` sotto `/prodotti/**` e `/aste/**`. `/admin/**`
richiede `ADMIN`, `/me/**` richiede `USER`; `DELETE /me` richiede un utente
autenticato. Gli altri percorsi richiedono almeno l'autenticazione. Una nuova
operazione riservata sotto un percorso pubblico, anche se è un `GET`, deve avere
una regola HTTP più specifica, posta prima della regola pubblica.

`@EnableMethodSecurity` abilita `@PreAuthorize` sui metodi dei bean Spring.
I nuovi servizi devono dichiarare esplicitamente le regole di ruolo o di
proprietà della risorsa che servono: la sola abilitazione non assegna permessi
ai metodi. Per esempio `hasRole('ADMIN')` richiede il ruolo ADMIN; nel servizio
di eliminazione account il `sub` del token deve corrispondere all'ID passato al
metodo. Qui `authentication` è un `JwtAuthenticationToken`, `principal` è il
JWT, `authentication.name` è lo username e `authentication.token.subject` è
l'ID utente.

`POST /api/v1/auth/register` accetta `username`, `email` e `password`, crea un
utente con ruolo `USER` e un portafoglio iniziale a zero. Restituisce `201` con
`id`, `username`, `email` e `ruolo`; username o email già in uso restituiscono
`409`.

`POST /api/v1/auth/login` accetta `username` e `password`. Restituisce un
`accessToken` JWT, `tokenType: "Bearer"`, `expiresAt`, `refreshToken`,
`refreshExpiresAt`, `userId`, `username` e `ruolo`. Credenziali errate o un
account disattivato restituiscono `401`. La Consumer conserva entrambi i token
nella propria sessione server-side e invia l'access token al Producer con
`Authorization: Bearer <accessToken>`. L'access token dura 30 minuti e il
refresh token 7 giorni. Il Producer verifica a ogni richiesta che la sessione
sia valida e che l'utente esista, sia attivo e abbia il ruolo necessario.

`POST /api/v1/auth/refresh` accetta `{ "refreshToken": "..." }` senza header
Bearer e restituisce lo stesso formato del login. Ogni rinnovo sostituisce il
refresh token precedente; quello vecchio restituisce `401`. La scadenza della
sessione resta quella fissata al login. `POST /api/v1/auth/logout` richiede
`Authorization: Bearer <accessToken>` e restituisce `204` senza body. Revoca la
sessione corrente: tutti gli access token e il refresh token di quella sessione
diventano inutilizzabili. La Consumer deve eliminare entrambi i token dalla
propria sessione dopo il logout.

`DELETE /api/v1/me` richiede `Authorization: Bearer <accessToken>` e
restituisce `204` senza body. Disattiva l'account (`attivo = false`), sostituisce
username, email e hash della password con valori anonimi e revoca tutte le
sessioni dell'utente. Gli ID e le relazioni storiche restano nel database.
Login, refresh e access token già emessi non funzionano più. Dopo la risposta,
la Consumer elimina i token dalla propria sessione. Username ed email originali
possono essere registrati nuovamente.

## Catalogo

Gli endpoint catalogo e gestione categorie/prodotti sono implementati nel
branch Prodotti. `POST /prodotti/{id}/acquisti` resta un contratto futuro:
richiede l'integrazione atomica con portafoglio, ledger e inventario e non ha
ancora un controller. Non fa parte della gestione catalogo completata qui.

`GET /categorie` restituisce un array di categorie attive ordinate per nome;
`GET /admin/categorie` include anche quelle disattivate. Ogni categoria ha
`id`, `nome`, `slug`, `attiva`.

`POST /admin/categorie` restituisce `201`; `PUT /admin/categorie/{id}` restituisce
`200`. Entrambi richiedono `nome` non vuoto (max 100) e `slug` (max 100), composto
da lettere minuscole, cifre e trattini separatori, per esempio `informatica`.
`attiva` è facoltativo: vale true in creazione e conserva il valore corrente
in modifica. Nome e slug sono univoci.

```json
{ "nome": "Informatica", "slug": "informatica", "attiva": true }
```

Il catalogo e il dettaglio pubblici mostrano solo prodotti attivi di categorie
attive. Un prodotto nascosto o inesistente restituisce `404 RISORSA_NON_TROVATA`.
Disattivare una categoria non modifica stock, flag dei prodotti o aste;
riattivarla ripristina la visibilità dei prodotti ancora attivi.
Le letture ADMIN includono anche prodotti di categorie disattivate.

`GET /prodotti` e `GET /admin/prodotti` accettano `query` (ricerca senza
distinzione maiuscole/minuscole su nome, SKU e descrizione; `%` e `_` letterali),
`categoria` (slug), `astabile` (boolean), `page` (default 0, minimo 0) e `size`
(default 12, da 1 a 100). Solo ADMIN accetta anche `attivo`; se omesso include
prodotti attivi e inattivi. Ordinamento stabile per nome e ID.

```http
GET /api/v1/prodotti?query=laptop&categoria=informatica&astabile=true&page=0&size=12
```

```json
{
  "content": [{
    "id": 1,
    "sku": "INF-LAP-001",
    "nome": "Laptop Pro 15",
    "descrizione": "Laptop per lo studio",
    "prezzoFisso": 1299.90,
    "astabile": true,
    "quantitaDisponibile": 3,
    "quantitaBloccata": 1,
    "asteProgrammate": 1,
    "attivo": true,
    "categoria": { "id": 1, "nome": "Informatica", "slug": "informatica", "attiva": true },
    "versione": 0,
    "dataCreazione": "2026-10-07T08:00:00Z"
  }],
  "page": 0,
  "size": 12,
  "totalElements": 1,
  "totalPages": 1
}
```

`astabile` è un campo persistito, non è derivato da altri valori. La possibilità
effettiva di programmare una nuova asta richiede anche
`quantitaDisponibile > 0`.

`asteProgrammate` conta le aste in `PROGRAMMATA`, `STANZA_APERTA` e `APERTA`,
escludendo `CHIUSA` e `ANNULLATA`. Il dettaglio restituisce lo stesso DTO prodotto
presente in `content`, senza l'involucro di paginazione.

### Creazione e modifica prodotto ADMIN

`POST /admin/prodotti` restituisce `201` e il DTO prodotto. Richiede
`categoriaId` positivo ed esistente, `sku` non vuoto (max 30), `nome` non vuoto
(max 200), `astabile` boolean e `quantitaDisponibile` intera non negativa.
`descrizione` è facoltativa (max 5000); `prezzoFisso` è facoltativo o null,
altrimenti deve essere positivo con max 10 cifre intere e 2 decimali.
SKU normalizzato in maiuscolo senza spazi esterni; nome e descrizione vengono
ripuliti dagli spazi esterni. Un prodotto nuovo è attivo, con quantità bloccata
e versione iniziali a zero. L'ADMIN può gestire prodotti in categorie inattive,
che restano nascosti al pubblico.

`PUT /admin/prodotti/{id}` restituisce `200` e sostituisce tutti i campi
modificabili: usa gli stessi campi del POST e richiede anche `attivo` e
`versione` non negativa, ottenuta dall'ultima lettura. Campi facoltativi omessi
o null vengono azzerati. Esempio:

```json
{
  "categoriaId": 1,
  "sku": "INF-LAP-001",
  "nome": "Laptop Pro 15",
  "descrizione": "Laptop per lo studio",
  "prezzoFisso": 1299.90,
  "astabile": true,
  "quantitaDisponibile": 3,
  "attivo": true,
  "versione": 0
}
```

`quantitaBloccata`, `id` e `dataCreazione` non sono campi modificabili.
La disattivazione tramite PUT sostituisce la cancellazione fisica e conserva
relazioni e storici; non è esposto un endpoint DELETE del catalogo.

Errori catalogo in `application/problem+json`:

| Status | Caso/codice |
| --- | --- |
| 400 | DTO, parametri o paginazione non validi |
| 401 | operazione ADMIN senza autenticazione |
| 403 | operazione ADMIN con ruolo USER |
| 404 | `RISORSA_NON_TROVATA` (prodotto/categoria assenti o dettaglio pubblico nascosto) |
| 409 | `CATEGORIA_GIA_ESISTENTE` (nome o slug duplicati) |
| 409 | `SKU_GIA_UTILIZZATO` |
| 409 | `VERSIONE_NON_AGGIORNATA` (rileggere prima di riprovare) |

## Portafoglio

Entrambe le operazioni sono implementate e riservate a `USER`. Il Producer
ricava l'utente dal token Bearer; non accetta ID di altri utenti. Anche i metodi
di servizio verificano ruolo e corrispondenza del subject con `@PreAuthorize`.

### Saldo e movimenti

```http
GET /api/v1/me/portafoglio?page=0&size=20
```

`page` parte da zero; `size` è compresa tra 1 e 100. I movimenti sono ordinati
per `dataMovimento` decrescente, poi `id` decrescente. Una pagina oltre l'ultima
restituisce una lista vuota. Saldi, conteggio e movimenti appartengono allo
stesso snapshot transazionale, anche durante rilanci o settlement concorrenti.

```json
{
  "saldoTotale": 10000.00,
  "saldoRiservato": 1250.00,
  "saldoDisponibile": 8750.00,
  "valuta": "CRD",
  "movimenti": [
    {
      "id": 42,
      "astaId": 7,
      "tipo": "RISERVA_OFFERTA",
      "importo": 1250.00,
      "saldoTotaleDopo": 10000.00,
      "saldoRiservatoDopo": 1250.00,
      "dataMovimento": "2026-10-09T07:00:00Z"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1
}
```

I tipi di movimento sono quelli della migrazione V8: `IMPOSTAZIONE_SALDO`,
`RISERVA_OFFERTA`, `RILASCIO_OFFERTA`, `PAGAMENTO_ASTA`, `INCASSO_ASTA`,
`ACQUISTO_FISSO`. `astaId` è null per operazioni non legate a un'asta.
Gli importi sono positivi; i saldi successivi descrivono l'effetto del movimento.
Non sono esposti entity, informazioni di altri utenti o credenziali.

### Impostazione del saldo virtuale

```http
PUT /api/v1/me/portafoglio/impostazioni
```

```json
{ "saldoTotale": 10000.00 }
```

`saldoTotale` è obbligatorio, non negativo, con massimo dieci cifre intere e
due decimali (`9999999999.99`). È un valore assoluto, non una ricarica da sommare.
La risposta `200` contiene `saldoTotale`, `saldoRiservato`, `saldoDisponibile`
e `valuta`, come nell'esempio del GET, senza la pagina dei movimenti.

Il servizio blocca prima l'utente, poi il portafoglio. Non cambia le riserve
esistenti e verifica il saldo riservato dopo l'attesa dei lock. Un cambiamento
crea un movimento `IMPOSTAZIONE_SALDO`, con `astaId` null, importo pari al
valore assoluto della differenza e saldi successivi. Saldo, versione, istante
UTC di modifica e ledger vengono aggiornati nella stessa transazione.
Un PUT con saldo uguale a quello corrente non crea movimenti né cambia versione
o data di modifica. Anche il saldo zero è consentito se non ci sono riserve.

Errori in `application/problem+json`:

| Status | Caso/codice |
| --- | --- |
| 400 | Body, importo o parametri non validi; `PAGINAZIONE_NON_VALIDA` per page < 0 o size fuori da 1–100 |
| 401 | Token assente, scaduto o revocato; account disattivato |
| 403 | Ruolo diverso da USER |
| 404 | `RISORSA_NON_TROVATA` per lettura di un portafoglio mancante |
| 409 | `SALDO_INFERIORE_AL_RISERVATO` per impostazione inferiore ai crediti già riservati |

Le operazioni economiche condivise con offerte e settlement sono descritte
in [Portafoglio e ledger](09-portafoglio.md).

## Programmazione di un'asta

```http
POST /api/v1/admin/aste
```

```json
{
  "prodottoId": 1,
  "inizioLocale": "2026-10-03T18:30:00",
  "timeZone": "Europe/Rome",
  "prezzoIniziale": 500.00
}
```

Il server:

1. verifica il ruolo ADMIN;
2. converte la data locale in UTC;
3. blocca la riga prodotto;
4. verifica `astabile = true` e `quantitaDisponibile > 0`;
5. sposta una unità da disponibile a bloccata;
6. crea l'asta con stato `PROGRAMMATA`, apertura stanza a `startsAt - 3m` e
   `endsAt = startsAt + 7m`.

L'ADMIN creatore è identificato dal `sub` del token autenticato, non dal body.
Il servizio richiede ruolo ADMIN e verifica che l'ID creatore corrisponda al
`sub` e a un utente attivo con ruolo ADMIN nel database.

Risposta `201` con `Location: /api/v1/aste/42`.

Il body usa il DTO di creazione, con una sintesi del prodotto e orari UTC:

```json
{
  "id": 42,
  "stato": "PROGRAMMATA",
  "prodotto": { "id": 1, "nome": "Laptop Pro 15" },
  "prezzoIniziale": 500.00,
  "incrementoMinimo": 1.00,
  "aperturaStanzaAt": "2026-10-03T16:27:00Z",
  "inizioAt": "2026-10-03T16:30:00Z",
  "fineAt": "2026-10-03T16:37:00Z",
  "serverTime": "2026-10-02T10:30:00Z",
  "sequence": 0
}
```

L'apertura della stanza è calcolata a meno tre minuti; la transizione automatica
di stato viene gestita dal motore temporale. La risposta non contiene entity
JPA, credenziali o dati dell'ADMIN.

Errori rilevanti:

- `422 PRODOTTO_NON_ASTABILE`;
- `409 PRODOTTO_NON_DISPONIBILE`;
- `422 DATA_INIZIO_NON_VALIDA`;
- `422 PREZZO_INIZIALE_NON_VALIDO`.

Gli stessi codici `422` si applicano a data, fuso e prezzo mancanti o non
validi nel body. JSON malformato o `prodottoId` mancante/non positivo producono
`400`; un prodotto inesistente produce `404 RISORSA_NON_TROVATA`.
Gli errori del body e di dominio sono restituiti come `application/problem+json`.

## Lobby delle aste

`GET /api/v1/aste` è pubblico e implementato. Accetta:

- `stato`: uno dei valori `PROGRAMMATA`, `STANZA_APERTA`, `APERTA`, `CHIUSA`,
  `ANNULLATA`; se omesso, include tutti gli stati.
- `categoria`: slug esatto della categoria del prodotto.
- `query`: testo cercato nel nome, SKU o descrizione del prodotto, senza
  distinzione tra maiuscole e minuscole. Gli spazi esterni vengono rimossi;
  `%`, `_` e gli altri caratteri vengono trattati come testo letterale.
- `page`: indice da zero, predefinito `0`.
- `size`: da `1` a `100`, predefinito `12`.

I filtri si combinano; categoria e testo vuoti equivalgono a filtri assenti.
L'ordinamento è `inizioAt` crescente, poi `id` crescente. Le aste restano
consultabili anche se il prodotto viene disattivato. Una pagina oltre i
risultati o filtri senza corrispondenze restituiscono `200` e `content: []`.

```http
GET /api/v1/aste?stato=APERTA&categoria=informatica&query=laptop&page=0&size=12
```

```json
{
  "content": [{
    "id": 42,
    "stato": "APERTA",
    "prodotto": { "id": 1, "nome": "Laptop Pro 15" },
    "prezzoIniziale": 500.00,
    "incrementoMinimo": 1.00,
    "offertaCorrente": 630.00,
    "numeroOfferte": 2,
    "aperturaStanzaAt": "2026-10-03T16:27:00Z",
    "inizioAt": "2026-10-03T16:30:00Z",
    "fineAt": "2026-10-03T16:37:40Z",
    "offerteConsentite": true,
    "sequence": 4
  }],
  "page": 0,
  "size": 12,
  "totalElements": 1,
  "totalPages": 1,
  "serverTime": "2026-10-03T16:31:00Z"
}
```

Pagina, dimensione, stato o ID non interpretabili producono `400` in
`application/problem+json`. Il servizio applica anche il controllo della
paginazione (`400 PARAMETRI_NON_VALIDI`) quando viene invocato direttamente.
Il totale e i risultati della pagina vengono letti dalla stessa fotografia
transazionale del database.

## Snapshot asta

`GET /api/v1/aste/{id}` è pubblico e implementato. Un'asta inesistente
restituisce `404 RISORSA_NON_TROVATA` in `application/problem+json`.

```json
{
  "id": 42,
  "stato": "STANZA_APERTA",
  "prodotto": { "id": 1, "nome": "Laptop Pro 15" },
  "prezzoIniziale": 500.00,
  "incrementoMinimo": 1.00,
  "offertaCorrente": null,
  "migliorOfferente": null,
  "numeroOfferte": 0,
  "aperturaStanzaAt": "2026-10-03T16:27:00Z",
  "inizioAt": "2026-10-03T16:30:00Z",
  "fineAt": "2026-10-03T16:37:00Z",
  "serverTime": "2026-10-03T16:28:05Z",
  "offerteConsentite": false,
  "sequence": 1
}
```

In `CHIUSA` lo snapshot include anche:

```json
{
  "vincitore": { "id": 9, "displayName": "g***i" },
  "prezzoFinale": 630.00,
  "chiusaAt": "2026-10-03T16:38:40Z"
}
```

`numeroOfferte` conta le offerte persistite; `migliorOfferente` identifica
l'autore dell'offerta più alta, con username mascherato come `g***i`.
Senza offerte, `offertaCorrente` e `migliorOfferente` sono `null` e il conteggio
è zero. `prezzoFinale` deriva da `offertaCorrente` solo in `CHIUSA` con vincitore;
senza vincitore, `vincitore` e `prezzoFinale` vengono omessi. I campi di esito
non sono esposti prima della chiusura; `chiusaAt` è presente se registrato.

Stato, sequence, timer, leader e conteggio provengono da una sola lettura
SQL, anche in presenza di rilanci concorrenti. Non vengono esposte entity JPA,
email, credenziali o dati dell'ADMIN. `offerteConsentite` è `true` solo se lo
stato è `APERTA` e `inizioAt <= serverTime < fineAt`; indica la disponibilità
temporale, mentre l'operazione di offerta verifica anche ruolo e crediti.
Il timer conserva eventuali estensioni già persistite.

Entrambe le letture restituiscono `Cache-Control: no-store`, orari UTC e tempo
server; non attivano aste e non modificano stock. Le transizioni restano
responsabilità dello scheduler. Il ticket WebSocket è implementato nel modulo
auth; annullamento e storici restano previsti dal contratto e non implementati.

## Storici

`GET /api/v1/me/vittorie` mostra esclusivamente le aste vinte dall'utente
autenticato, con prodotto, importo e data. `GET /api/v1/admin/aste/storico`
restituisce tutte le aste concluse e permette filtri per prodotto, vincitore e
intervallo temporale.

## WebSocket/STOMP

Handshake: `ws://localhost:8081/ws?ticket={ticketMonouso}`.

Il ticket può essere richiesto solo da tre minuti prima dell'inizio. Prima di
quel momento il server risponde `409 STANZA_NON_APERTA`.
`POST /api/v1/aste/{id}/ticket` richiede `ROLE_USER` e restituisce `200` con:

```json
{
  "ticket": "valore-casuale-monouso",
  "expiresAt": "2026-10-03T16:27:30Z"
}
```

Il ticket è legato all'utente, alla sessione di login e all'asta; scade dopo
30 secondi e viene consumato al primo handshake. È conservato in memoria nel
Producer, quindi i ticket ancora inutilizzati non sopravvivono a un riavvio.
L'handshake senza ticket valido restituisce `401`. Un'asta inesistente
restituisce `404 RISORSA_NON_TROVATA`; un'asta annullata non emette ticket.
Il browser può sottoscrivere soltanto il topic dell'asta associata al ticket e
la propria coda `/user/queue/aste`. Non può pubblicare direttamente su
`/topic` o `/queue`. La validità della sessione di login viene ricontrollata
per ogni comando STOMP.

| Direzione | Destinazione | Messaggio |
| --- | --- | --- |
| client → server | `/app/aste/{id}/join` | ingresso stanza |
| client → server | `/app/aste/{id}/offerte` | rilancio, solo in `APERTA` |
| server → stanza | `/topic/aste/{id}` | eventi pubblici ordinati |
| server → utente | `/user/queue/aste` | conferme/rifiuti privati e presenza iniziale |

Il client si sottoscrive al topic e alla coda privata prima di inviare
`/app/aste/{id}/join`. Il comando `join` non richiede un body. La stanza conta
utenti distinti: più schede dello stesso utente non aumentano il numero dei
presenti. Dopo ogni `join` il Producer risponde **solo alla sessione che ha
inviato il comando** con la presenza corrente:

```json
{
  "type": "PRESENCE_SNAPSHOT",
  "auctionId": 42,
  "participantCount": 2,
  "participants": ["g***i", "l***a"],
  "serverTime": "2026-10-03T16:28:05Z"
}
```

Quando entra la prima scheda di un utente, il topic riceve `USER_JOINED`;
quando esce la sua ultima scheda riceve `USER_LEFT`. Le disconnessioni ripetute
non producono eventi duplicati. Entrambi gli eventi riportano il nome
mascherato, il numero dei presenti e l'elenco completo aggiornato, così il
client può sostituire la vista della presenza:

```json
{
  "type": "USER_JOINED",
  "auctionId": 42,
  "displayName": "g***i",
  "participantCount": 2,
  "participants": ["g***i", "l***a"],
  "serverTime": "2026-10-03T16:28:05Z"
}
```

I messaggi di presenza non contengono `sequence`.

Comando offerta:

```json
{
  "type": "PLACE_BID",
  "clientBidId": "5ab0d96e-c7b0-42d1-a74b-97180d9849b8",
  "importo": 630.00,
  "knownSequence": 8
}
```

Evento pubblico accettato:

`knownSequence` arretrata non impedisce un'offerta ancora valida sullo stato
corrente; il risultato del servizio indica `snapshotRequired=true`.
Una sequenza futura produce `SEQUENCE_NON_AGGIORNATA`. Il retry di un UUID già
accettato non ripete riserve, estensioni o eventi; con dati diversi produce
`CLIENT_BID_ID_GIA_UTILIZZATO`. Il comando STOMP è implementato nel modulo
WebSocket e delega a `OffertaService`, che mantiene tutte le regole economiche.
L'identità deriva esclusivamente dal ticket verificato; non da un ID nel body.

```json
{
  "type": "BID_ACCEPTED",
  "auctionId": 42,
  "sequence": 9,
  "importo": 630.00,
  "offerenteDisplay": "g***i",
  "numeroOfferte": 9,
  "fineAt": "2026-10-03T16:38:40Z",
  "serverTime": "2026-10-03T16:34:12Z",
  "extensionSeconds": 20
}
```

Rifiuto privato per fondi insufficienti:

```json
{
  "type": "BID_REJECTED",
  "clientBidId": "5ab0d96e-c7b0-42d1-a74b-97180d9849b8",
  "code": "SALDO_INSUFFICIENTE",
  "message": "Saldo disponibile insufficiente",
  "snapshotRequired": false
}
```

### Conferme private e adattamento eventi (8 ottobre 2026)

Ogni comando accettato, incluso un retry, restituisce `BID_CONFIRMED` soltanto
alla sessione STOMP mittente (`/user/queue/aste`, non alle altre schede dello
stesso utente). Il messaggio contiene `clientBidId`, `duplicata`, `ritirata`,
`snapshotRequired` e `stato`, uno snapshot pubblico dei campi economici correnti:

```json
{
  "type": "BID_CONFIRMED",
  "clientBidId": "5ab0d96e-c7b0-42d1-a74b-97180d9849b8",
  "duplicata": false,
  "ritirata": false,
  "snapshotRequired": false,
  "stato": {
    "type": "AUCTION_SNAPSHOT",
    "auctionId": 42,
    "sequence": 9,
    "serverTime": "2026-10-03T16:34:12Z",
    "stato": "APERTA",
    "prodottoId": 12,
    "offertaCorrente": 630.00,
    "offerenteDisplay": "g***i",
    "numeroOfferte": 9,
    "fineAt": "2026-10-03T16:38:40Z",
    "extensionSeconds": 0
  }
}
```

I campi non applicabili possono essere `null`. Lo stato privato non incrementa
`sequence` e non sostituisce lo snapshot REST completo: se `snapshotRequired`
è true, il client recupera `GET /api/v1/aste/{id}`. Un retry non ripubblica
`BID_ACCEPTED` e non applica una nuova estensione.

Gli errori di dominio diventano `BID_REJECTED` alla sola sessione mittente.
Per una sequenza futura `snapshotRequired=true`; payload non validi, UUID
illeggibili o violazioni Jakarta producono `DATI_NON_VALIDI` (UUID null se non
recuperabile). Le violazioni di autorizzazione sul canale STOMP possono invece
chiudere la connessione prima di arrivare al controller.

Il relay ascolta con `@EventListener` gli eventi già post-commit e inoltra
`ROOM_OPENED`, `AUCTION_STARTED`, `BID_ACCEPTED`, `AUCTION_CLOSED` e
`AUCTION_SNAPSHOT` al topic dell'asta. Gli eventi economici hanno campi piatti
come nell'esempio `BID_ACCEPTED`; lo snapshot di ritiro include lo stato e il
leader aggiornati, la chiusura include `vincitoreDisplay` mascherato (null
senza vincitore). Non espone `migliorOfferenteId`, `vincitoreId` o l'UUID del
comando nel topic pubblico. Non genera email né avvia il settlement.
La consegna resta best effort in memoria: riconnessioni e gap richiedono REST.

In `main` un listener separato del modulo `notifica` accoda l'email
al vincitore con persistenza/retry. Non cambia il protocollo STOMP né aggiunge
endpoint REST. L'invio resta disabilitato finché SMTP non è configurato; dettagli
in [Notifiche email](10-notifiche-email.md).

### Decisione sulla `sequence` e sulla presenza

`aste.sequence` è un contatore persistito per singola asta. Il Producer lo
incrementa nella stessa transazione che modifica lo stato dell'asta e pubblica
l'evento soltanto dopo il commit. Hanno una `sequence` crescente gli eventi di
stato `ROOM_OPENED`, `AUCTION_STARTED`, `BID_ACCEPTED`, `AUCTION_CLOSED` e
`AUCTION_CANCELLED` e lo snapshot di ritiro `AUCTION_SNAPSHOT`. Il client
confronta questi numeri con la propria ultima
`sequence`: applica il successivo, ignora i duplicati o gli eventi più vecchi e,
se rileva un salto, recupera `GET /api/v1/aste/{id}` prima di riprendere gli
aggiornamenti. Lo snapshot REST riporta la `sequence` corrente senza
incrementarla.

Un'offerta accettata incrementa `sequence` **una sola volta**. L'evento
`BID_ACCEPTED` include già `fineAt` aggiornato ed `extensionSeconds`; non si
pubblica un secondo evento `TIMER_EXTENDED` per lo stesso rilancio.

La presenza è temporanea e non modifica `aste.sequence`. Gli eventi di
presenza, per esempio `USER_JOINED` e `USER_LEFT`, non contengono `sequence` e
non partecipano al controllo dei gap. Al `join` e dopo ogni riconnessione il
client riceve la presenza corrente tramite WebSocket; per prezzo, leader,
stato e timer recupera lo snapshot REST. Dopo un riavvio la presenza riparte
vuota e si ricostruisce con le nuove connessioni.

Questa distinzione precisa le formulazioni generali sugli eventi con
`sequence` in `docs/01-requisiti.md` e `docs/07-liveauction.md`.

## Errori principali

| Status/canale | Codice |
| --- | --- |
| 401 | `AUTENTICAZIONE_RICHIESTA` |
| 403 | `OPERAZIONE_NON_CONSENTITA` |
| 404 | `RISORSA_NON_TROVATA` |
| 409 | `STANZA_NON_APERTA` |
| 409 | `ASTA_NON_APERTA` |
| 409 | `PRODOTTO_NON_DISPONIBILE` |
| 409 | `OFFERTA_SUPERATA` |
| 409 | `RILANCIO_SU_SE_STESSO` |
| 409 | `CLIENT_BID_ID_GIA_UTILIZZATO` |
| 409 | `LIMITE_SALDO_SUPERATO` |
| 409 | `SALDO_INSUFFICIENTE` |
| 422 | `PRODOTTO_NON_ASTABILE` |
| 422 | `DATA_INIZIO_NON_VALIDA` |
| coda privata | `SEQUENCE_NON_AGGIORNATA` |
