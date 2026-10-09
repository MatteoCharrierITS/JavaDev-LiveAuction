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

**[DA FARE — attività e prossimi passi del team](todo.md)**

Report di integrazione: [attenzioni dopo il collegamento WebSocket](report-integrazione-websocket.md).

## Stato del modulo aste

Nel branch corrente sono implementati:

- `POST /api/v1/admin/aste`: creazione ADMIN, input `Europe/Rome`, importi
  validati e prenotazione atomica di una unità.
- `GET /api/v1/aste`: lobby pubblica con filtri e paginazione.
- `GET /api/v1/aste/{id}`: snapshot pubblico con timer, offerte persistite,
  sequence e partecipanti mascherati.
- Scheduler: passaggi a `STANZA_APERTA` e `APERTA`, recupero all'avvio ed
  eventi Spring dopo il commit collegati ai topic WebSocket.
  Il job non richiama ancora la chiusura.
- Ticket monouso, STOMP e presenza temporanea; comando `/app/aste/{id}/offerte`
  con conferme/rifiuti alla sola sessione mittente.
- Servizi JDBC di rilancio, estensione, riserva/rilascio con ledger, ritiro offerte
  e settlement con assegnazione all'inventario; eventi economici sui topic.
- Email al vincitore dopo il commit, con coda persistente V11, recupero e retry;
  invio SMTP disabilitato per default.

Chiusura automatica, annullamento, storici, API inventario
e attivazione SMTP email restano da completare. Il settlement è già implementato nel servizio
interno, ma non viene invocato automaticamente alla scadenza.
Le API del portafoglio sono integrate in `main`: saldo e movimenti
paginati, impostazione del saldo virtuale e operazioni JDBC condivise con le aste.
Dettagli in [Portafoglio e ledger](09-portafoglio.md).
La UI Consumer resta un modulo separato.

Payload, filtri ed errori sono descritti in [Contratti REST](04-api-rest.md);
il funzionamento del job è in [Motore LiveAuction](07-liveauction.md).
La [collection Postman](postman/README.md) indica quali richieste sono
disponibili. Le verifiche aggiornate di questa integrazione sono nel report
WebSocket sopra indicato.

## Decisioni chiave

In `main`, dal commit `c9e2a11`, è disponibile anche il
[modulo email al vincitore](10-notifiche-email.md), con coda persistente e retry.
L'invio è disabilitato per default; attivazione SMTP e chiusura automatica
restano da completare.

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
| Vincita | unità assegnata al vincitore e registrata negli storici |
| Live | WebSocket/STOMP più snapshot REST |
| Persistenza | PostgreSQL + Flyway |

## Fuori perimetro

Pagamenti reali, spedizioni integrate, chat libera, immagini caricate dagli
utenti, broker esterno e scalabilità multi-nodo. L'email al vincitore è una
notifica informativa e non determina la validità della chiusura.
