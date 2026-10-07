# Report breve — integrazione WebSocket

8 ottobre 2026, branch `web_socket`. Modifiche locali, nessun commit.

Implementati: relay degli eventi post-commit verso i topic, comando STOMP di
rilancio delegato a `OffertaService`, conferme/rifiuti alla sola sessione
mittente e pulizia dei fixture dei test. Il trasporto non modifica le regole
economiche né lo schema Flyway.

## 1. Chiusura automatica ancora da collegare

**Problema:** `AstaScheduler` seleziona solo aste PROGRAMMATA/STANZA_APERTA e
richiama `AstaLifecycleService` per aprirle. Non richiama
`ChiusuraAstaService.chiudi` alla scadenza: un'asta APERTA scaduta rifiuta nuovi
rilanci, ma non viene automaticamente regolata né produce AUCTION_CLOSED.

**Consiglio di fix:** Marco e Tommi colleghino al job la selezione delle aste
APERTA con `fine_at <= now`, sia nei cicli periodici sia al riavvio. Ogni asta
va chiusa tramite il servizio transazionale/idempotente esistente, con errori
isolati e retry al ciclo successivo. Non implementare settlement nel WebSocket.
Verificare scadenza estesa, concorrenza rilancio/chiusura, riavvio, chiusura senza
leader e assenza di trasferimenti/eventi duplicati.

## 2. Documentazione e confini dei moduli economici

**Problema:** alcune sezioni indicavano rilanci/settlement come non implementati
e inventario come sola tabella. In realtà `OfferteRepository` opera già via JDBC
su portafogli, ledger e inventario; mancano le API dedicate, non tutte le
operazioni economiche. Il branch portafoglio non integrato va ancora riallineato
allo schema, senza introdurre una seconda implementazione incompatibile.

**Intervento fatto:** aggiornati indice/stato documentazione, motore live,
contratto STOMP, note offerte/chiusura, piano del team e istruzioni Postman.
Documentata la nuova conferma privata BID_CONFIRMED e il comportamento dei retry.

**Consiglio di fix:** Mondir e Tommi concordino proprietà/interfacce delle
operazioni già esistenti e rispettino ordine dei lock, versioni e ledger.
Non cambiare V2/V8 applicate e non duplicare riserve o pagamenti. Assegnare nel
team le API inventario; nessuna area libera viene attribuita da questo intervento.

## Verifiche

Suite completa Producer: **190 test superati, 0 fallimenti, 0 errori, 0 saltati**,
con PostgreSQL reale e connessioni WebSocket/STOMP reali. Comando eseguito:

```sh
RUN_DB_TESTS=true RUN_WS_TESTS=true ASTE_SCHEDULER_ENABLED=false \
DB_URL=jdbc:postgresql://localhost:5433/liveauction_ws_verified_20261008 \
sh producer/mvnw -f producer/pom.xml clean test
```

Database nuovo e dedicato, senza cancellare dati preesistenti. I job automatici
sono disattivati durante la suite per non modificare i fixture di altri contesti;
i test del ciclo di vita richiamano esplicitamente il job/servizio.
Corretto anche il conteggio globale in `AstaLifecycleIntegrationTests`: ora
controlla gli eventi delle proprie aste, senza includere fixture di altre suite.
Controllo SQL finale: zero utenti e prodotti residui dei test WebSocket.
Nessuna modifica alla logica di produzione del ciclo di vita.
