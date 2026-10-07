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

## Decisioni chiave

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
