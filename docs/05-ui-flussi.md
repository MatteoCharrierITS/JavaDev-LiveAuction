# Interfaccia e flussi utente

Stato della copia di lavoro al 14 ottobre 2026. La UI Consumer include
autenticazione, catalogo, portafoglio, lobby, stanza live, gestione ADMIN,
vittorie personali, storico ADMIN e annullamento. Usa le API del Producer;
L'inventario personale è ora disponibile tramite API e pagina `/inventario`;
gli acquisti a prezzo fisso sono disponibili dal dettaglio prodotto per USER.
Le sezioni seguenti distinguono le pagine disponibili dalle
funzioni ancora previste.

## Rotte Consumer

| Rotta | Accesso | Pagina |
| --- | --- | --- |
| `GET/POST /login` | pubblico | accesso |
| `GET/POST /registrazione` | pubblico | nuovo account |
| `GET /account`, `POST /account/delete`, `POST /logout` | autenticato; POST con CSRF | account, eliminazione e logout |
| `GET /marketplace` | pubblico | catalogo filtrabile e paginato |
| `GET /prodotti/{id}` | pubblico | dettaglio, stock e astabilità |
| `GET /aste` | pubblico | lobby filtrabile e paginata |
| `GET /aste/{id}` | USER | stanza LiveAuction |
| `GET /aste/{id}/snapshot` | USER | snapshot Producer per la sincronizzazione browser, senza cache |
| `GET /aste/{id}/wallet` | USER | soli saldi del portafoglio reale tramite token nella sessione Consumer |
| `POST /aste/{id}/ticket` | USER + CSRF | solo ticket STOMP monouso al browser |
| `GET /me/vittorie` | USER | storico personale vittorie, paginato |
| `GET /inventario` | USER | prodotti posseduti e quantità, paginati, compresi quelli non più pubblici |
| `POST /prodotti/{id}/acquisti` | USER + CSRF | quantità 1–100 e UUID, acquisto a prezzo fisso |
| `GET/POST /impostazioni/portafoglio` | USER; POST con CSRF | saldi, impostazione del totale e ledger paginato |
| `GET /admin/prodotti`, `GET /admin/prodotti/nuovo`, `GET /admin/prodotti/{id}/modifica` | ADMIN | catalogo, creazione e modifica prodotto |
| `POST /admin/prodotti`, `POST /admin/prodotti/{id}` | ADMIN + CSRF | creazione e aggiornamento prodotto |
| `GET /admin/categorie`, `GET /admin/categorie/{id}/modifica` | ADMIN | gestione categorie, anche inattive |
| `POST /admin/categorie`, `POST /admin/categorie/{id}` | ADMIN + CSRF | creazione e aggiornamento categoria |
| `GET /admin/aste/nuova`, `POST /admin/aste` | ADMIN; POST con CSRF | programmazione asta |
| `GET /admin/aste/storico` | ADMIN | storico globale, filtri e paginazione |
| `POST /admin/aste/{id}/annullamento` | ADMIN + CSRF | annullamento dalla lobby; verifica finale del Producer |

La lobby mostra l'annullamento agli ADMIN per aste attive senza offerte visibili;
il Producer ricontrolla stato, scadenza e ogni offerta ricevuta, incluse quelle
ritirate. Il form acquisto compare per USER su prodotti con prezzo e stock
disponibile. Dopo conferma si rilegge l'inventario; portafoglio e prodotto sono
consultabili per verificare i valori correnti. In caso di esito sconosciuto
(timeout/5xx), la pagina conserva UUID e quantità per un retry senza doppio
addebito. Un nuovo caricamento del prodotto genera invece un nuovo UUID.
Il server usa il prezzo corrente sotto lock; la pagina lo dichiara esplicitamente.

Le scritture dei form usano POST Consumer con CSRF; il Consumer invia POST o PUT
al Producer con il Bearer conservato nella sessione server-side.

Il browser genera un UUID v4 per ogni comando di rilancio, conservandolo nei
retry. Su HTTP tramite IP LAN/VPN `crypto.randomUUID()` non è disponibile:
la stanza usa in quel caso `crypto.getRandomValues()` per generare l'UUID,
così l'invio dell'offerta funziona anche senza un contesto HTTPS/localhost.

## Implementazioni mancanti

- **Filtri aggiuntivi della lobby:** “In partenza” e “a cui partecipo” restano
  proposte senza filtri REST dedicati e non sono presenti nella UI.
- **Esito nella card della lobby:** per `CHIUSA` la card indica soltanto che i
  dettagli del vincitore non sono disponibili nella lobby. Il vincitore e il
  prezzo finale sono mostrati nella stanza e nello storico ADMIN quando previsti
  dai rispettivi DTO.
- **Feed persistente dei rilanci:** la stanza mostra solo eventi ricevuti mentre
  la pagina è aperta. Il Producer non espone un GET per ricostruire il feed
  dopo un reload; il riallineamento dello stato usa lo snapshot REST.

Verifica locale del 13 ottobre: 45 test Java Consumer e 10 test JavaScript
superati. Le verifiche web usano controller MVC con Producer simulato e test
JavaScript della stanza; la prova completa con due utenti e più schede su
Producer, PostgreSQL e WebSocket reali resta nel TODO di integrazione.

## Catalogo

Il marketplace mostra solo prodotti attivi di categorie attive. Il pannello
ADMIN consulta anche gli elementi disattivati. Il form di modifica prodotto
invia tutti i campi modificabili e la `versione` dell'ultima lettura; in caso di
`409 VERSIONE_NON_AGGIORNATA` ricarica il prodotto prima di riproporre la modifica.
La pagina mostra i valori tentati e quelli correnti, e richiede un nuovo invio
esplicito dell'ADMIN. La versione corrente entra nel successivo PUT.
La quantità bloccata è visualizzata ma non modificabile nel form.

Ogni card del catalogo indica testualmente:

- `AVAILABLE FOR AUCTION` oppure `NOT AUCTIONABLE`;
- quantità disponibile; il dettaglio mostra anche la quantità bloccata;
- numero di aste programmate o live.

Il pannello ADMIN permette di modificare `astabile` e stock. Il collegamento
`Schedule` compare solo per i prodotti idonei; negli altri casi la UI indica
`Auction disabled` o `No available stock`.

## Programmazione ADMIN

Il form contiene:

- prodotto selezionabile tra quelli astabili e disponibili;
- data e ora di inizio tramite controllo `datetime-local`;
- indicazione fissa del fuso `Europe/Rome`;
- prezzo iniziale in CRD;
- riepilogo: apertura stanza `-3 min`, durata `7 min`, `+20s` per rilancio.

Dopo la conferma, una unità risulta bloccata e l'asta appare nella lobby.

## Lobby

Filtri disponibili nella REST: stato, categoria e testo, con paginazione.
“In partenza” e “a cui partecipo” sono proposte di UI senza filtri REST dedicati:
il loro comportamento va concordato prima di mostrarli come disponibili. Ogni card
mostra prezzo iniziale o corrente, numero offerte e uno di questi countdown:

- `Room opens in` per `PROGRAMMATA`;
- `Auction starts in` per `STANZA_APERTA`;
- `Ends in` per `APERTA`;
- nessun countdown per `CHIUSA` o `ANNULLATA`; la card mostra lo stato senza
  dettagli del vincitore.

## Stanza LiveAuction

La pagina contiene stato e countdown, prezzo iniziale e incremento minimo,
offerta corrente e leader, form del rilancio, saldi del portafoglio, presenza e
feed degli eventi ricevuti. Le date sono presentate nel fuso `Europe/Rome`.

Durante `STANZA_APERTA` lo stesso layout mostra utenti presenti e countdown
all'inizio, ma il form delle offerte è disabilitato. Al termine compare un
annuncio con vincitore e importo finale, oppure `Auction closed without bids.`.

La UI mostra stati di connessione in inglese, tra cui `Live`, `Reconnecting…`
e `Synchronizing…`. Dopo la riconnessione il form resta disabilitato finché
non arriva uno snapshot valido.
Gli ultimi venti secondi sono evidenziati rispettando `prefers-reduced-motion`.

Il browser usa `LIVEAUCTION_PRODUCER_WS_URL` (default `ws://localhost:8081/ws`)
per collegarsi direttamente al Producer; l'URL deve essere raggiungibile dal
browser, non soltanto dal container Consumer. Il token Bearer resta nella
sessione server-side della Consumer. Il feed mostra solo gli eventi ricevuti
durante la sessione della pagina: il Producer non espone ancora un endpoint GET
per ricostruire lo storico dei rilanci dopo il reload.

Per un deployment su un'origine diversa da `http://localhost:8082`, il Producer
deve configurare esplicitamente la nuova origine WebSocket consentita; in
produzione HTTPS, configurare un URL `wss://`. Questa modifica richiede il
coordinamento con il modulo Producer senza ampliare indiscriminatamente le
origini ammesse.

## Flussi principali

### Programmare

Login ADMIN → prodotti → verifica `astabile` e quantità → data/ora e prezzo
iniziale → conferma → unità bloccata → asta in lobby.

### Partecipare

Login USER → lobby → apertura stanza tre minuti prima → ticket WS → join →
attesa → evento `AUCTION_STARTED` → rilanci → chiusura e annuncio vincitore.

### Vincere

Chiusura → addebito crediti → assegnazione persistita nell'inventario → voce in
`My victories` → email riepilogativa post-commit. La consultazione
dell'inventario non è ancora disponibile nella UI.

### Riconnettersi

WebSocket perso → indicatore offline → nuovo ticket → subscribe → snapshot REST
→ confronto `sequence` → riabilitazione comandi solo se l'asta è `APERTA`.

### Consultare lo storico ADMIN

Login ADMIN → storico aste → filtri per prodotto, vincitore, stato e intervallo
temporale → card con prodotto, ID del vincitore se presente, prezzo finale e
date. La card non espone prezzo iniziale o numero di offerte.
