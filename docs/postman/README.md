# Collection Postman

Importare `local.postman_environment.json` e selezionare l'ambiente
**LiveAuction • Locale**. Importare poi le collection desiderate:

| File | Modulo | Stato su `main` |
| --- | --- | --- |
| `monitoring.json` | Health e Ready | Implementato |
| `auth.json` | Registrazione, login, refresh, logout ed eliminazione account | Implementato |
| `prodotti.json` | Categorie, catalogo e gestione ADMIN | Implementato su `main`; acquisto fisso nella cartella `Contratto futuro — non implementato` |
| `inventario.json` | Inventario personale | Contratto futuro — non implementato su `main` |
| `portafoglio.json` | Saldo, movimenti paginati e impostazioni | Implementato |
| `aste.json` | Lobby, snapshot, ticket e programmazione | Implementato su `main`; annullamento, vittorie e storico ADMIN nella cartella `Contratto futuro — non implementato` |

L'ambiente usa `http://localhost:8081/api/v1` come `baseUrl`. Modificarlo se
il Producer è esposto su un'altra porta. Le migrazioni non creano account demo:
la richiesta 01 registra un utente USER. I token restano
nelle variabili dell'ambiente Postman e non sono salvati nei file del progetto.

Eseguire le richieste 01–04 e 07–09 di `auth.json` per verificare registrazione,
validazione, login, refresh e logout. Le richieste 05 e 06 sono facoltative:
impostare prima le credenziali di account esistenti nell'ambiente. Per un ADMIN,
registrare l'utente e assegnargli il ruolo nel database come spiegato nel
[README principale](../../README.md). I login impostano i token di accesso e
refresh nell'ambiente. La richiesta 08 rinnova i token USER; la 09 chiude la
sessione USER e va eseguita per ultima. I token di accesso `userToken` e
`adminToken` sono usati automaticamente dalle altre collection. Le richieste di registrazione generano un nome
diverso a ogni esecuzione.
La richiesta 10 elimina e anonimizza l'account: eseguirla manualmente dopo
un nuovo login, senza includerla nella normale sequenza di test.

Per `prodotti.json`, effettuare prima il login ADMIN, poi eseguire le richieste
01–10 in ordine. La creazione categoria salva `categoriaId`; la creazione e
la lettura ADMIN del prodotto salvano `prodottoId`, `prodottoVersione` e il
body completo `prodottoModifica`. Modificare quest'ultimo nell'ambiente per
aggiornare il prodotto, conservando la versione dell'ultima lettura. La risposta
al PUT aggiorna anche il body per il prossimo invio. In caso di 409 sulla
versione, rileggere il prodotto (09) e riapplicare la modifica desiderata.
Per provare la visibilità, disattivare la categoria (04): catalogo e dettaglio
pubblici nascondono i suoi prodotti, mentre le letture ADMIN li conservano.
La richiesta di acquisto fisso si trova nella cartella `Contratto futuro — non implementato`: è documentativa, può restituire `404` e non va inclusa nella sequenza principale di verifica catalogo su `main`.

Le altre collection seguono [il contratto REST](../04-api-rest.md). Finché i
relativi controller non saranno sviluppati, una risposta `404` è attesa dopo
aver soddisfatto le regole di accesso; senza il token o ruolo richiesto la
sicurezza può restituire `401` o `403` prima di raggiungere il controller. I body
segnati come **provvisori** vanno aggiornati quando il modulo definirà i propri
DTO. Impostare `prodottoId` e `astaId` nell'ambiente usando ID esistenti; la
richiesta di programmazione salva `astaId` dalla risposta `Location`, se
presente.

In `aste.json`, **Programma asta ADMIN** usa un prodotto esistente, astabile e
con stock disponibile, oltre a `adminToken`. Genera la data di domani alle
12:00 nel fuso `Europe/Rome`, verifica `201`, il DTO e gli orari UTC e salva
`astaId`. La richiesta prenota una unità: ripeterla richiede altro stock.
**Lobby delle aste** è pubblica e verifica paginazione e tempo server; per
provare i filtri aggiungere `stato`, `categoria` (slug) e `query` alla URL.
**Snapshot asta** è pubblico e usa `astaId` per verificare stato, timer,
conteggio e sequence. Gli username di offerente e vincitore sono mascherati.
Il ticket è implementato e richiede un USER e una stanza accessibile da tre
minuti prima dell'inizio. Annullamento e storici restano previsti e possono
rispondere `404` fino all'implementazione.

Il protocollo STOMP su `/ws` richiede un client WebSocket: `aste.json` include
la richiesta REST del ticket, ma non i comandi e le sottoscrizioni STOMP.

## Convenzione di disponibilità

Le sequenze principali delle collection devono verificare solo endpoint
realmente disponibili su `main`.

Le richieste nella cartella `Contratto futuro — non implementato` servono come
riferimento al contratto API: finché i controller non esistono su `main`, una
risposta `404` è attesa (oppure `401`/`403` se mancano token o ruolo).

I branch personali (`Prodotti`, `Aste`, `feature/*`, `web_socket`) non
rappresentano funzionalità integrate nel codice di riferimento.

Per provare i rilanci, usare un client STOMP con il ticket monouso ottenuto dalla
collection, sottoscrivere `/topic/aste/{astaId}` e `/user/queue/aste`, quindi
inviare `PLACE_BID` a `/app/aste/{astaId}/offerte` come nel
[contratto STOMP](../04-api-rest.md#websocketstomp). L'asta deve essere APERTA e
l'utente deve avere saldo disponibile: impostarlo con la richiesta PUT della
collection `portafoglio.json`.
Il mittente riceve `BID_CONFIRMED` o `BID_REJECTED`; il topic riceve gli eventi
post-commit. Un retry dello stesso UUID non ripubblica l'evento pubblico.
La chiusura è un servizio interno, non un endpoint REST manuale.

Per `portafoglio.json`, effettuare il login USER: il GET verifica saldi e pagina
dei movimenti; il PUT imposta il saldo virtuale assoluto (esempio: 10000 CRD).
Ripetere il GET dopo il PUT mostra il movimento `IMPOSTAZIONE_SALDO`. Un PUT
identico non aggiunge movimenti. Le due API non sono accessibili agli ADMIN.
