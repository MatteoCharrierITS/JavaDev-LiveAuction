# Collection Postman

Importare `local.postman_environment.json` e selezionare l'ambiente
**LiveAuction • Locale**. Importare poi le collection desiderate:

| File | Modulo | Stato |
| --- | --- | --- |
| `monitoring.json` | Health e Ready | Implementato |
| `auth.json` | Registrazione, login, refresh, logout ed eliminazione account | Implementato |
| `prodotti.json` | Categorie, catalogo e gestione ADMIN | Implementato; acquisti fissi futuri |
| `inventario.json` | Inventario personale | API prevista |
| `portafoglio.json` | Saldo e impostazioni | API previste |
| `aste.json` | Lobby, ticket, programmazione e storici | Programmazione ADMIN, lobby, snapshot e ticket implementati; altre API previste |

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
La richiesta FUTURO per acquisto fisso va eseguita solo dopo l'implementazione
del modulo transazionale; non includerla nella sequenza di verifica catalogo.

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

Per provare i rilanci, usare un client STOMP con il ticket monouso ottenuto dalla
collection, sottoscrivere `/topic/aste/{astaId}` e `/user/queue/aste`, quindi
inviare `PLACE_BID` a `/app/aste/{astaId}/offerte` come nel
[contratto STOMP](../04-api-rest.md#websocketstomp). L'asta deve essere APERTA e
l'utente deve avere saldo disponibile: le API di ricarica sono ancora future.
Il mittente riceve `BID_CONFIRMED` o `BID_REJECTED`; il topic riceve gli eventi
post-commit. Un retry dello stesso UUID non ripubblica l'evento pubblico.
La chiusura è un servizio interno, non un endpoint REST manuale.
