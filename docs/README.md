# Documentazione di progettazione

Questa è la specifica corrente di **LiveAuction**.

## Indice

1. [Requisiti e regole di dominio](01-requisiti.md)
2. [Architettura e comunicazione](02-architettura.md)
3. [Modello dati PostgreSQL](03-database.md)
4. [Contratti REST e WebSocket](04-api-rest.md)
5. [Interfaccia e flussi utente](05-ui-flussi.md)
6. [Roadmap e criteri di accettazione](06-piano-lavoro.md)
7. [Motore LiveAuction](07-liveauction.md)
8. [Offerte e chiusura: integrazione](08-offerte-chiusura.md)
9. [Portafoglio e ledger](09-portafoglio.md)
10. [Notifiche email al vincitore](10-notifiche-email.md)
11. [Verifica e allineamento della documentazione](11-verifica-documentazione.md)
12. [Consegna di Marco: annullamento e storici](12-annullamento-storici.md)

**[DA FARE — attività e prossimi passi del team](todo.md)**

Report di integrazione: [attenzioni dopo il collegamento WebSocket](report-integrazione-websocket.md).

## Stato del modulo aste

In `main`, verificato il 12 ottobre 2026 sulla base `4b82f1e`, sono implementati:

- `POST /api/v1/admin/aste`: creazione ADMIN, input `Europe/Rome`, importi
  validati e prenotazione atomica di una unità.
- `GET /api/v1/aste`: lobby pubblica con filtri e paginazione.
- `GET /api/v1/aste/{id}`: snapshot pubblico con timer, offerte persistite,
  sequence, leader e vincitore mascherati. La lista e il numero dei presenti
  sono trasportati via STOMP, non nello snapshot REST.
- Scheduler: passaggi a `STANZA_APERTA` e `APERTA`, recupero all'avvio ed
  eventi Spring dopo il commit collegati ai topic WebSocket.
  Il job richiama anche la chiusura delle aste
  APERTA scadute, con recupero al riavvio e retry isolati per asta.
- Ticket monouso, STOMP e presenza temporanea; comando `/app/aste/{id}/offerte`
  con conferme/rifiuti alla sola sessione mittente.
- Servizi JDBC di rilancio, estensione, riserva/rilascio con ledger, ritiro offerte
  e settlement con assegnazione all'inventario; eventi economici sui topic.
- Email al vincitore dopo il commit, con coda persistente V11, recupero e retry;
  invio SMTP disabilitato per default. Template di configurazione, controlli
  STARTTLS/TLS implicito e test SMTP locale sono già integrati in `main`.

Nelle modifiche locali di Marco del 12 ottobre 2026 sono implementati anche
annullamento ADMIN (senza offerte), evento AUCTION_CANCELLED, vittorie USER
e storico ADMIN con filtri e paginazione. V12 conserva la data di annullamento.
API inventario e acquisti a prezzo fisso restano da completare. L'attivazione SMTP esterna è fuori scope per la consegna. La chiusura automatica e la correzione dello
snapshot dopo il ritiro delle offerte sono integrate in `main`.
Le API del portafoglio sono integrate in `main`: saldo e movimenti
paginati, impostazione del saldo virtuale e operazioni JDBC condivise con le aste.
Dettagli in [Portafoglio e ledger](09-portafoglio.md).
Nella copia di lavoro del 13 ottobre la UI Consumer include autenticazione,
marketplace, portafoglio, lobby, stanza live, gestione ADMIN, vittorie personali,
storico ADMIN e annullamento. Inventario e acquisti a prezzo fisso attendono API
e pagine; il [documento UI](05-ui-flussi.md#implementazioni-mancanti) elenca
anche le differenze fra la proposta iniziale e le pagine disponibili.

Payload, filtri ed errori sono descritti in [Contratti REST](04-api-rest.md);
il funzionamento del job è in [Motore LiveAuction](07-liveauction.md).
La [collection Postman](postman/README.md) indica quali richieste sono
disponibili. Verifica storica riportata del risultato del merge `3bcc9cc`: **230 test Producer e 1 test
Consumer superati**, senza fallimenti, errori o test saltati, con PostgreSQL,
WebSocket e SMTP simulato. Dettagli in [Offerte e chiusura](08-offerte-chiusura.md#test).
Il report WebSocket sopra indicato conserva lo stato storico dell'8 ottobre.
La verifica locale del 12 ottobre supera 96 test Producer e 1 Consumer, con
104 test di integrazione saltati; non sostituisce la suite PostgreSQL/WebSocket.
Vedere il [report di allineamento](11-verifica-documentazione.md).
La verifica delle modifiche di Marco supera **268 test Producer e 1 Consumer**,
nessuno saltato, con PostgreSQL, WebSocket e SMTP locale. Entrambe le build
`verify` sono riuscite; dettagli nel [report di consegna](12-annullamento-storici.md).
I conteggi Consumer in questi report sono storici e precedono le pagine attuali.

## Decisioni chiave

In `main`, dal commit `c9e2a11`, è disponibile anche il
[modulo email al vincitore](10-notifiche-email.md), con coda persistente e retry.
L'invio è disabilitato per default; l'attivazione SMTP esterna è fuori scope. La chiusura
automatica è collegata in `main` dal merge `3bcc9cc`.

| Tema | Decisione |
| --- | --- |
| Gestore aste | solo `ADMIN` |
| Programmazione | data/ora e prezzo iniziale scelti dall'ADMIN |
| Fuso orario | input `Europe/Rome`, persistenza UTC |
| Apertura stanza | 3 minuti prima dell'inizio |
| Durata asta | 7 minuti iniziali |
| Anti-sniping | +20 secondi a ogni rialzo valido |
| Tempo | sempre deciso dal Producer |
| Denaro | crediti finti in portafoglio |
| Fondi | riserva atomica della migliore offerta |
| Prodotto | campo `astabile` esplicito e stock disponibile/bloccato |
| Catalogo pubblico | prodotto e categoria entrambi attivi; ADMIN consulta anche i disattivati |
| Modifica prodotto | PUT completo con `versione`; stock bloccato gestito dalle aste |
| Vincita | assegnazione persistita; API storici e pagine Consumer per vittorie e storico ADMIN presenti |
| Live | WebSocket/STOMP più snapshot REST |
| Persistenza | PostgreSQL + Flyway |

## Fuori perimetro

Pagamenti reali, spedizioni integrate, chat libera, immagini caricate dagli
utenti, broker esterno e scalabilità multi-nodo. L'email al vincitore è una
notifica informativa e non determina la validità della chiusura.
