# Prossimi passi per il team

Documento aggiornato al 7 ottobre 2026, dopo l'integrazione in `main` dei branch
`Prodotti`, `Aste` e `feature/logica_aste` (commit `5362757`). Serve a chiarire
cosa è fatto, cosa manca e cosa deve fare ciascuno da adesso.

Aggiornamento del branch `web_socket`, 8 ottobre 2026: comando di rilancio,
relay degli eventi e pulizia dei fixture WebSocket implementati. Questa nota
non implica che le modifiche locali siano già state mergiate in `main`.
Verifica dell'integrazione: 190 test Producer superati, nessuno saltato,
con PostgreSQL e WebSocket reali; dettagli nel
[report WebSocket](report-integrazione-websocket.md).

## 1. Stato integrato e aggiornamento del branch `web_socket`

| Area | Referente | Stato |
| --- | --- | --- |
| Autenticazione e utenti | Matteo | Completa: registrazione, login, refresh, logout, eliminazione account, ruoli, JWT |
| WebSocket e notifiche | Maikol | Nel branch web_socket: STOMP, ticket, presenza, comando rilancio ed eventi stanze fatti. Manca email al vincitore |
| Prodotti e catalogo | Cristian | Completa: categorie, prodotti, CRUD ADMIN, ricerca, paginazione, stock disponibile/bloccato |
| Programmazione aste | Marco | Quasi completa: programmazione ADMIN, apertura automatica, lobby, snapshot. Mancano annullamento e storici |
| Offerte e chiusura | Tommi | Servizi JDBC con riserve/ledger e assegnazione inventario; rilancio esposto via STOMP nel branch web_socket. Chiusura non collegata al job |
| Portafoglio e movimenti | Mondir | **Non integrato** (vedi sezione 2) |
| Inventario (`/me/inventario`) | da assegnare | API mancante; assegnazione del prodotto già gestita nel settlement JDBC |
| Acquisti a prezzo fisso | da assegnare | Non iniziato |
| Consumer (UI Thymeleaf) | da assegnare | Solo scheletro, nessuna pagina |

Verifica eseguita prima del push: 179 test del Producer con PostgreSQL, 0 falliti;
il Consumer compila e supera i suoi test.

## 2. Perché `portafoglio-movimenti` non è in `main`

Le entità del branch non corrispondono allo schema Flyway. Con
`ddl-auto=validate` il Producer non si avvia (`missing table [movimenti]`).

| Nel branch | Nello schema (V2 e V8) |
| --- | --- |
| tabella `wallet` | `portafogli` |
| tabella `movimenti` | `movimenti_portafoglio` |
| `wallet_id` | `portafoglio_id` |
| `data_creazione`, `descrizione`, `offerta_id` | `data_movimento`, `asta_id`, `saldo_totale_dopo`, `saldo_riservato_dopo` |
| `RICARICA`, `RISERVA`, `RILASCIO`, `ADDEBITO` | `IMPOSTAZIONE_SALDO`, `RISERVA_OFFERTA`, `RILASCIO_OFFERTA`, `PAGAMENTO_ASTA`, `INCASSO_ASTA`, `ACQUISTO_FISSO` |

Lo schema è il riferimento (`docs/03-database.md`). Non si modificano V2 e V8: si
adattano le entità.

## 3. Cosa fa ciascuno

### Mondir: portafoglio e movimenti (priorità massima, sblocca gli altri)

1. Aggiornare il branch con `main` (`git merge origin/main`).
2. Riallineare `Wallet`, `Movimento` e `TipoMovimento` allo schema (tabella sopra).
3. Verificare che il Producer si avvii e che i test passino con `RUN_DB_TESTS=true`.
4. Implementare `GET /me/portafoglio` (saldo totale, riservato, disponibile e
   movimenti) come da `docs/04-api-rest.md`.
5. Ogni modifica al saldo deve produrre un movimento di ledger; usare `BigDecimal`.
6. Esporre ai servizi di Tommi le operazioni di riserva, rilascio, pagamento e
   incasso, da concordare insieme prima di scrivere il codice.
7. Aprire la richiesta di merge solo a test verdi.

### Tommi: offerte e chiusura

1. Coordinare le interfacce con il WebSocket: `OffertaService` è richiamato dal
   comando STOMP nel branch web_socket. `ChiusuraAstaService` resta interno;
   `RitiroOfferteService` è già collegato all'eliminazione account. Non esporre
   un endpoint pubblico di settlement.
2. Collegare la chiusura automatica allo scheduler delle aste di Marco.
3. Concordare con Mondir la convivenza o estrazione delle operazioni JDBC già
   esistenti di riserva, rilascio, ledger e settlement. L'assegnazione inventario
   è implementata; mancano le API dedicate e il referente per completarle.
4. Pubblicare gli eventi tramite `EventiOffertePublisher`, d'accordo con Maikol.
5. Aggiornare Postman e documentazione per gli endpoint nuovi.

### Maikol: WebSocket e notifiche

1. Completato nel branch web_socket: relay delle transizioni e di
   `EventiOffertePublisher` ai topic STOMP, comando rilancio delegato al servizio
   e conferme/rifiuti alla sola sessione mittente. Il WebSocket non decide la
   validità economica delle offerte.
2. Implementare l'email al vincitore dopo la chiusura.
3. Completato: i test ticket/presenza e i nuovi test rilanci rimuovono i propri
   fixture in `@AfterEach`, anche in caso di fallimento. Scheduler disattivato
   nei fixture per evitare interferenze con le verifiche dei trasporti.
4. Eseguire `PresenceWebSocketIntegrationTests` con `RUN_WS_TESTS=true`: senza
   questa variabile viene saltato.

### Marco: programmazione aste

1. Completare l'annullamento delle aste e gli endpoint dei storici previsti dal
   contratto.
2. Controllare il test `dueJobConcorrentiNonDuplicanoStatiOEventi` in
   `AstaLifecycleIntegrationTests`: è fallito una volta su cinque esecuzioni e poi
   è passato. È un test di concorrenza, va reso stabile.
3. Aggiornare Postman e `docs/04-api-rest.md` per i nuovi endpoint.

### Cristian: prodotti e catalogo

1. Il catalogo è completo. Verificare che documentazione e Postman riflettano
   lo stato reale dopo il merge.
2. Gli acquisti a prezzo fisso dipendono dal portafoglio di Mondir (movimento
   `ACQUISTO_FISSO`) e dall'inventario. Da decidere nel team chi li realizza.

### Matteo: autenticazione

1. L'area è completa. Restare disponibile per ruoli e sicurezza delle nuove
   rotte: ogni nuovo endpoint protetto va definito sia nel contratto sia con
   `@PreAuthorize`.
2. Attenzione alle letture riservate sotto `/prodotti/**` e `/aste/**`: i `GET` lì
   sono pubblici, quindi una regola più specifica deve precedere quella pubblica.
3. Proposta, da confermare: seguire il flusso di login lato Consumer (token nella
   sessione server-side) quando verrà assegnata l'area.

## 4. Da decidere insieme

Queste aree non hanno un referente. Non vanno attribuite senza una decisione del team.

1. **Inventario**: `GET /me/inventario` e relativo modulo. L'assegnazione al
   vincitore è già implementata dal settlement JDBC di Tommi.
2. **Acquisti a prezzo fisso**.
3. **Consumer**: client REST, pagine Thymeleaf, marketplace, lobby, stanza live,
   inventario, portafoglio, pannello ADMIN. È la parte più grande rimasta.

## 5. Ordine consigliato delle dipendenze

1. Mondir riallinea il portafoglio e concorda con Tommi l'uso delle operazioni
   economiche JDBC già implementate, senza duplicarle.
2. Marco e Tommi collegano la chiusura automatica al servizio esistente.
3. Si assegna il modulo/API inventario, senza riscrivere l'assegnazione già
   implementata nel settlement.
4. Maikol completa l'email; relay e comando STOMP sono nel branch web_socket.
5. Si assegna e si avvia il Consumer sui contratti già disponibili. Lobby e
   catalogo si possono fare subito, senza aspettare il resto.

## 6. Regole per tutti

- Prima di iniziare, aggiornare il proprio branch con `main`.
- Prima di aprire una richiesta di merge, eseguire i test completi con database:
  `docker compose up -d postgres`, poi
  `RUN_DB_TESTS=true ./producer/mvnw -f producer/pom.xml test` (su Windows
  `mvnw.cmd`). Non usare `docker compose down -v` se il volume contiene dati da
  conservare.
- Non modificare le migrazioni già applicate: aggiungere nuove migrazioni
  versionate. L'ultima è V10.
- Le entità devono corrispondere allo schema. `ddl-auto=validate` blocca l'avvio
  se non coincidono.
- I test devono usare dati unici e rimuovere ciò che creano, per non influenzare
  i test degli altri (è l'errore che ha mostrato il test sulle ricerche).
- Tenere le modifiche nel package del proprio dominio e concordare le modifiche
  alle interfacce condivise.
- Aggiornare documentazione e collection Postman a ogni cambio di contratto API.
- Non scrivere password o token nei log.
- Dopo l'integrazione si possono eliminare i branch remoti già in `main`
  (`Prodotti`, `Aste`, `feature/logica_aste`). `portafoglio-movimenti` va
  mantenuto finché Mondir non lo ha sistemato.
