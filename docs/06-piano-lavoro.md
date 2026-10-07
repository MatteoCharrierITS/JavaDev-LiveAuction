# Roadmap e criteri di accettazione

## Stato del branch Prodotti — 7 ottobre 2026

- Implementati catalogo pubblico, ricerca, filtri, paginazione, categorie e
  creazione/modifica ADMIN di prodotti e categorie, con DTO e Problem Details.
- Il catalogo pubblico richiede prodotto e categoria attivi; l'ADMIN può
  consultare anche gli elementi disattivati. La riattivazione non altera stock.
- Modifica prodotto completa con controllo `versione`; quantità bloccata
  riservata alle operazioni del dominio aste.
- Contratto REST e collection Postman aggiornati agli endpoint implementati.
- Acquisto fisso ancora previsto, non implementato: integrazione transazionale
  di prodotto, portafoglio, ledger e inventario da coordinare tra i moduli.
  La responsabilità di questa integrazione resta da concordare dal team.
- WebSocket e monitoring presenti su `main`, non nel branch Prodotti: al merge
  conservare in SecurityConfig i permessi già presenti su `main` e aggiungere
  quello pubblico per `GET /api/v1/categorie`.

Verifica con un database di prova:

```bash
RUN_DB_TESTS=true DB_URL=jdbc:postgresql://localhost:5433/liveauction_test sh producer/mvnw -f producer/pom.xml clean test
```

Sul risultato integrato con `main`, abilitare anche `RUN_WS_TESTS=true`.

Verifica del 7 ottobre 2026 su PostgreSQL 16: 9 test superati nel branch
Prodotti (6 scenari catalogo); 24 test superati nella copia temporanea del merge
con `main`, includendo le modifiche al codice e ai test del catalogo, senza
errori né test saltati. Coperti disattivazione/riattivazione categoria,
letture ADMIN, validazione, conteggio aste non concluse, versione obsoleta e
due transazioni concorrenti sullo stock (un solo commit). Verificati inoltre
JSON, sostituzione delle variabili nei body e script della collection Postman;
la collection non è stata eseguita tramite il runner Postman.

## Fase 1 — Identità, catalogo e portafoglio

- Security, registrazione, login e ruoli USER/ADMIN.
- Entity utenti, prodotti, inventario, wallet e ledger.
- Campi prodotto `astabile`, `quantitaDisponibile` e `quantitaBloccata`.
- Migrazioni Flyway, una per tabella, senza dati iniziali.
- Catalogo e pannello amministrativo.

## Fase 2 — Programmazione e motore transazionale

- Tabelle/entity `aste` e `offerte`.
- Programmazione ADMIN con data/ora `Europe/Rome` e prezzo iniziale.
- Lock prodotto e blocco atomico di una unità.
- Stati `PROGRAMMATA`, `STANZA_APERTA`, `APERTA`, `CHIUSA`, `ANNULLATA`.
- Apertura stanza a meno tre minuti e asta di sette minuti.
- Offerta con lock, idempotenza e riserva fondi.
- Estensione esatta di venti secondi.
- Chiusura e assegnazione idempotente al vincitore.
- Test di concorrenza con PostgreSQL/Testcontainers.

## Fase 3 — REST e Consumer

- Sessione web e client autenticato.
- Marketplace, inventario, portafoglio e vittorie personali.
- Pagine ADMIN per prodotti, programmazione e storico globale.
- Lobby, pre-live e snapshot della stanza.
- Gestione errori quando il Producer non è disponibile.

## Fase 4 — LiveAuction WebSocket e notifiche

- Ticket breve e handshake STOMP.
- Topic stanza e coda privata.
- Countdown client sincronizzato per pre-live e live.
- Feed, annuncio vincitore, riconnessione e recupero `sequence`.
- Email post-commit al vincitore con retry degli errori.

## Matrice minima di test

| Scenario | Esito atteso |
| --- | --- |
| USER prova a programmare un'asta | `403` |
| prodotto con `astabile=false` | `422` |
| prodotto senza quantità disponibile | `409` |
| due programmazioni sull'ultima unità | una sola riesce |
| asta creata | disponibile -1, bloccata +1 |
| data locale valida | conversione UTC corretta |
| accesso oltre tre minuti prima | `STANZA_NON_APERTA` |
| join durante pre-live | consentito |
| offerta durante pre-live | rifiutata |
| raggiunto `startsAt` | asta `APERTA`, durata 7 minuti |
| prima offerta sotto il prezzo iniziale | rifiutata |
| saldo insufficiente | nessuna riserva, rifiuto |
| rialzo valido | nuovo leader, `endsAt + 20s` |
| leader superato | riserva precedente liberata |
| stesso `clientBidId` reinviato | nessun doppio addebito |
| due rialzi simultanei | ordine unico, un solo leader |
| offerta al limite | decide il tempo server |
| chiusura con vincitore | addebito e assegnazione una volta |
| chiusura senza offerte | unità sbloccata |
| asta annullata senza offerte | unità sbloccata |
| riavvio con transizione scaduta | stato recuperato |
| gap di `sequence` | client richiede snapshot |
| errore invio email | vittoria confermata, invio ritentabile |
| storico USER | solo vittorie dell'utente |
| storico ADMIN | tutte le aste concluse |

## Definition of Done

- Producer e Consumer restano applicazioni indipendenti.
- Solo l'ADMIN programma le aste.
- Nessuna asta nasce senza data/ora e prezzo iniziale validi.
- Nessuna unità può essere impegnata in due aste.
- Il dominio usa PostgreSQL reale nei test di integrazione critici.
- Nessun importo usa `double`.
- Non esistono percorsi che modificano saldo senza ledger.
- Nessuna asta può trasferire due volte crediti o prodotto.
- UI e API indicano esplicitamente `astabile` e stock.
- Stanza, durata ed estensione rispettano 3 minuti, 7 minuti e 20 secondi.
- Il Compose `prod` porta tutti i servizi allo stato healthy.
- Contratti e implementazione coincidono.
