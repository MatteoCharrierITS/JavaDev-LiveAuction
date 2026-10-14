# Collection Postman

Importare `local.postman_environment.json` e selezionare l'ambiente
**LiveAuction • Locale**. Importare poi le collection desiderate:

| File | Modulo | Stato |
| --- | --- | --- |
| `monitoring.json` | Health e Ready | Implementato |
| `auth.json` | Registrazione, login, refresh, logout ed eliminazione account | Implementato |
| `prodotti.json` | Categorie, catalogo, gestione ADMIN e acquisti USER | Implementato; acquisto/retry manuali 11–12 |
| `inventario.json` | Inventario personale | Implementato, USER, paginazione e controlli risposta |
| `portafoglio.json` | Saldo, movimenti paginati e impostazioni | Implementato |
| `aste.json` | Lobby, ticket, programmazione e storici | Programmazione, lobby, snapshot, ticket, annullamento e storici implementati |

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
Le richieste 11–12 verificano acquisto e retry con `userToken`: configurare prima
`SHOP_ADMIN_ID` con un ADMIN attivo e predisporre prezzo fisso, stock e saldo.
La 11 genera un nuovo `clientPurchaseId` e salva `acquistoEsito`; la 12 riusa
UUID e quantità e attende `200` con `duplicato=true`, senza nuovi effetti.
Non includerle nella sequenza di verifica catalogo ADMIN. Rileggere inventario,
portafoglio e prodotto per verificare quantità, addebito e stock.

Le collection seguono [il contratto REST](../04-api-rest.md): gli endpoint
elencati sono implementati. Senza il token o ruolo richiesto la sicurezza può
restituire `401` o `403` prima di raggiungere il controller.
Impostare `prodottoId` e `astaId` nell'ambiente usando ID esistenti; la
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
minuti prima dell'inizio. **Annulla asta ADMIN** non richiede body, libera lo
stock una sola volta e può essere ripetuta sullo stesso astaId. Con offerte
ricevute risponde 409 ASTA_CON_OFFERTE; chiusa/scaduta risponde 409
ASTA_NON_ANNULLABILE. **Le mie vittorie** e **Storico aste ADMIN** verificano
DTO e pagine (size default 20); aggiungere alla seconda prodottoId,
vincitoreId, stato CHIUSA/ANNULLATA, da/a UTC per i filtri combinati.

Lo snapshot espone il leader operativo, escludendo le offerte ritirate; il
conteggio delle offerte resta storico. Dopo la cancellazione del leader,
verificare che REST mostri il leader ripristinato oppure null. La chiusura
automatica è integrata in `main` (merge `3bcc9cc`), senza endpoint pubblico
di settlement: verificare l'esito tramite lo snapshot dopo la scadenza.

Il protocollo STOMP su `/ws` richiede un client WebSocket: `aste.json` include
la richiesta REST del ticket, ma non i comandi e le sottoscrizioni STOMP.

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

## Verifica del catalogo e uso del runner

Allineamento documentale del 12 ottobre 2026, base `main` `4b82f1e`:
metodi, URL, token ADMIN, body di creazione/modifica, versione e variabili della
collection `prodotti.json` corrispondono a controller, DTO e servizi del catalogo.
Le richieste 01–10 verificano ora status, struttura dei DTO, paginazione,
visibilità pubblica e valori delle scritture. Le risposte 05, 09 e 10 aggiornano
il body completo del PUT. La quantità bloccata resta esclusa dall'input.
Le variabili generate del catalogo sono dichiarate anche nell'ambiente esportato.

Nel runner selezionare **solo 01–10** di `prodotti.json` su un database di prova.
Le richieste acquisto/retry 11–12 sono separate e richiedono i prerequisiti sopra.
La collection `inventario.json` è eseguibile dopo il login USER. Le tre API di Marco in
`aste.json` sono implementate. Importare una collection non implica che tutte le sue API
siano disponibili. La collection aste richiede inoltre ID validi: programmare
prima un'asta; il ticket richiede una stanza già accessibile e non è un test
eseguibile subito sull'asta programmata per domani.

Verifiche manuali aggiuntive del catalogo, dopo la sequenza 01–10:

1. Impostare `attiva=false` nel body della richiesta 04 e inviarla. La 06 deve
   nascondere il prodotto, la 07 rispondere `404`, mentre 03, 08 e 09 continuano
   a mostrarlo. In questa prova il controllo standard `200` della 07 fallisce
   intenzionalmente: confrontare `404 RISORSA_NON_TROVATA` e Problem Details.
2. Riportare `attiva=true` nella 04: 06 e 07 devono mostrare nuovamente il
   prodotto senza variazioni dello stock.
3. Salvare una copia di `prodottoModifica`, cambiare un campo (per esempio nome)
   ed eseguire la 10. Reinviare la copia con la precedente versione: attendere
   `409 VERSIONE_NON_AGGIORNATA`, poi rileggere con la 09 e riapplicare la modifica.
   Un PUT identico potrebbe non cambiare versione: modificare realmente un campo.
4. Provare una lettura ADMIN con il token USER (`403`) e senza token (`401`).
   Provare `size=101` nel catalogo (`400`); verificare Problem Details sugli
   errori di validazione/catalogo. Sono casi negativi, da eseguire separatamente
   dalle richieste standard che attendono risposte di successo.

La revisione ha controllato JSON, sintassi JavaScript, sostituzione dei body e
corrispondenza con il codice. **Il runner Postman non è stato eseguito** e non
sono stati creati dati tramite le collection in questa revisione.
