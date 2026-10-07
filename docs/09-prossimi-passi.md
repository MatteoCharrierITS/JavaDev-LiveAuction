# Prossimi passi per il team

Documento aggiornato al 7 ottobre 2026, dopo l'integrazione in `main` dei branch
`Prodotti`, `Aste` e `feature/logica_aste` (commit `5362757`). Serve a chiarire
cosa è fatto, cosa manca e cosa deve fare ciascuno da adesso.

## 1. Stato attuale di `main`

| Area | Referente | Stato |
| --- | --- | --- |
| Autenticazione e utenti | Matteo | Completa: registrazione, login, refresh, logout, eliminazione account, ruoli, JWT |
| WebSocket e notifiche | Maikol | Parziale: STOMP, ticket e presenza fatti. Mancano eventi delle stanze e email al vincitore |
| Prodotti e catalogo | Cristian | Completa: categorie, prodotti, CRUD ADMIN, ricerca, paginazione, stock disponibile/bloccato |
| Programmazione aste | Marco | Quasi completa: programmazione ADMIN, apertura automatica, lobby, snapshot. Mancano annullamento e storici |
| Offerte e chiusura | Tommi | Logica nei servizi con test, ma **non esposta**: nessun endpoint REST/STOMP la richiama |
| Portafoglio e movimenti | Mondir | **Non integrato** (vedi sezione 2) |
| Inventario (`/me/inventario`) | da assegnare | Solo la tabella, nessun codice |
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

1. Esporre la logica già scritta (`OffertaService`, `ChiusuraAstaService`,
   `RitiroOfferteService`) con gli endpoint definiti in `docs/04-api-rest.md` e
   `docs/08-offerte-chiusura.md`, con ruoli e `@PreAuthorize` come da convenzioni.
2. Collegare la chiusura automatica allo scheduler delle aste di Marco.
3. Integrare il portafoglio di Mondir per riserva e rilascio dei crediti, e il
   trasferimento di crediti e prodotto al vincitore (richiede l'inventario, vedi
   sezione 4).
4. Pubblicare gli eventi tramite `EventiOffertePublisher`, d'accordo con Maikol.
5. Aggiornare Postman e documentazione per gli endpoint nuovi.

### Maikol: WebSocket e notifiche

1. Collegare `EventiOffertePublisher` ai topic STOMP delle stanze, con Tommi. Il
   WebSocket trasporta eventi e non valida le offerte.
2. Implementare l'email al vincitore dopo la chiusura.
3. Sistemare i test `WebSocketTicketIntegrationTests` e
   `PresenceWebSocketIntegrationTests`: lasciano nel database un'asta con SKU
   `SKU-ws_...`. Ho già tolto l'underscore dallo SKU (commit `5362757`), ma
   conviene che i test puliscano i dati che creano.
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

1. **Inventario**: `GET /me/inventario` e assegnazione del prodotto al vincitore.
   Serve a Tommi per chiudere un'asta.
2. **Acquisti a prezzo fisso**.
3. **Consumer**: client REST, pagine Thymeleaf, marketplace, lobby, stanza live,
   inventario, portafoglio, pannello ADMIN. È la parte più grande rimasta.

## 5. Ordine consigliato delle dipendenze

1. Mondir riallinea il portafoglio e lo integra in `main`.
2. Si assegna l'inventario.
3. Tommi collega offerte e chiusura a portafoglio e inventario.
4. Maikol collega eventi ed email.
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
