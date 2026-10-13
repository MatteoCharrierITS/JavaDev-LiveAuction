# LiveAuction — Spring Boot Producer/Consumer

> **[DA FARE — attività e prossimi passi per ogni persona](docs/todo.md)**
>
> Il riferimento del team per cosa manca, priorità e dipendenze. Aggiornalo quando completi o integri un'attività.

## Assegnazioni

| Persona | Area e funzionalità |
| --- | --- |
| **Matteo** | Autenticazione e utenti: registrazione, login, token, ruoli e sicurezza. |
| **Andrea** | Consumer/frontend: client REST verso il Producer, sessione web, pagine Thymeleaf e stanza live. |
| **Maikol** | WebSocket e notifiche: STOMP, ticket, presenza, eventi delle stanze ed email al vincitore. |
| **Cristian** | Prodotti e catalogo: categorie, CRUD ADMIN, ricerca, paginazione e stock. |
| **Matteo (subentro dal 9 ottobre 2026)** | Portafoglio e movimenti: riallineamento del lavoro di Mondir, API del saldo/ledger e integrazione delle operazioni sui crediti con Tommi. |
| **Marco** | Programmazione aste: creazione ADMIN, orari, blocco stock, scheduler, annullamento e storici. |
| **Tommi** | Offerte e chiusura: rilanci, estensioni, concorrenza, vincitore e trasferimento di crediti e prodotto. |
| **Da assegnare** | API inventario e acquisti a prezzo fisso; referente da decidere nel team. |

### Regole di collaborazione

Ogni responsabile cura anche DTO, validazioni, errori, migrazioni Flyway e test
della propria area. La logica economica rimane nel Producer: il WebSocket
trasporta gli eventi senza decidere la validità delle offerte, mentre la
Consumer non accede mai direttamente al database.

Marketplace didattico nel quale un amministratore programma aste live di
prodotti disponibili a catalogo e gli utenti partecipano usando crediti
virtuali. Il vincitore riceve una unità del prodotto nel proprio inventario.

Il sistema è composto da due applicazioni indipendenti:

- **producer** (`:8081`): API REST, autenticazione, motore d'asta WebSocket,
  portafogli e persistenza PostgreSQL;
- **consumer** (`:8082`): interfaccia Thymeleaf che usa esclusivamente i
  contratti esposti dal Producer.

> **Stato di main al 13 ottobre 2026 (base `c864b2f`, con revisione delle consegne):**
> Producer e Consumer includono il flusso delle aste: autenticazione e sessione
> web, catalogo, portafoglio, programmazione ADMIN, lobby, stanza STOMP,
> rilanci, riserve, chiusura automatica e recupero dopo riavvio. Sono integrati
> annullamento ADMIN senza offerte, evento AUCTION_CANCELLED, vittorie personali,
> storico ADMIN e relative pagine. Email con coda persistente e retry disponibili;
> SMTP esterno fuori scope. **Mancano API e pagine inventario e acquisti a prezzo
> fisso**; i referenti delle API devono essere decisi dal team, le pagine sono
> di Andrea. Il settlement già assegna il prodotto vinto nell'inventario.

La revisione delle modifiche `3dfeec2..c864b2f` ha verificato contratti,
autorizzazioni, isolamento delle vittorie, transazioni, stock, eventi post-commit,
sessione Consumer e trasporto STOMP. Corretto un problema della stanza: dopo
il rilancio di un altro utente il portafoglio viene riletto, così il precedente
leader vede la riserva liberata. Aggiunto un test JavaScript di regressione.

**Verifica del 13 ottobre:** `verify` Producer e Consumer riusciti, entrambi i
JAR prodotti; **268 test Producer, 45 test Java Consumer e 11 test JavaScript**
superati, zero fallimenti, errori o test saltati. PostgreSQL 16 in un container
temporaneo senza volumi del progetto, WebSocket reali e SMTP locale di test;
nessuna email reale inviata. Esecuzione host con Temurin 25.0.4, compilazione
`release 21` e Mockito caricato come agente esplicito per evitare il limite di
self-attach dell'ambiente. La conferma sul JDK 21 previsto resta nel collaudo finale.

I test Consumer simulano le risposte REST del Producer; i test JavaScript
simulano il browser/trasporto. Questa verifica non certifica il percorso completo
con due browser e lo stack Docker `prod`: sono attività aperte nel
[TODO dettagliato per persona](docs/todo.md). Le priorità sono assegnare e
completare le due API mancanti, integrare le pagine e collaudare il sistema finale.

Per ripetere la suite usare un database PostgreSQL **dedicato ai test**:

```sh
RUN_DB_TESTS=true RUN_WS_TESTS=true ASTE_SCHEDULER_ENABLED=false \
DB_URL=jdbc:postgresql://localhost:55432/liveauction_test \
DB_USERNAME=postgres DB_PASSWORD='<password-del-db-di-prova>' \
./producer/mvnw -f pom.xml verify

node --test consumer/src/test/js/auction-lobby.test.cjs consumer/src/test/js/auction-room.test.mjs
```

Le suite configurano o richiamano lo scheduler secondo lo scenario da verificare.
Se l'ambiente impedisce il self-attach di Mockito, aggiungere all'invocazione
Maven `-DargLine=-javaagent:/percorso/alla/cache/mockito-core-5.17.0.jar`.

Le verifiche precedenti sono conservate nei report storici:
[allineamento documentazione del 12 ottobre](docs/11-verifica-documentazione.md),
[consegna annullamento e storici](docs/12-annullamento-storici.md) e
[integrazione offerte/chiusura](docs/08-offerte-chiusura.md#test).
Non sostituiscono il collaudo del Consumer attuale.

## La feature distintiva: LiveAuction

Integrati in `main` (commit `c9e2a11`): email al vincitore,
coda persistente e retry; invio disabilitato per default, nessun invio reale
durante i test. Configurazione e limiti in [Notifiche email](docs/10-notifiche-email.md).

In `main` (commit `4b82f1e`) sono disponibili anche il template SMTP in
`.env.example`, STARTTLS obbligatorio/TLS implicito e test del flusso completo
WebSocket → chiusura via scheduler → email su SMTP locale via TCP. L'attivazione
del provider esterno è fuori scope e non verrà inclusa nella consegna.
La configurazione presente resta disponibile come riferimento tecnico in
[Notifiche email](docs/10-notifiche-email.md#attivazione-nellambiente-concordato).

L'ADMIN decide se un prodotto è astabile e, per ogni asta, imposta prodotto,
data e ora di inizio e prezzo iniziale. Il sistema riserva subito una unità:
non è possibile programmare un'asta se lo stock disponibile è terminato.

Ogni asta segue queste regole:

- la stanza diventa accessibile **3 minuti prima** dell'inizio;
- durante il pre-live gli utenti entrano e vedono i partecipanti, ma non
  possono offrire;
- la durata iniziale è di **7 minuti**;
- ogni offerta valida aggiunge **20 secondi** alla scadenza corrente;
- il timer è sincronizzato sul tempo del server;
- prezzo, miglior offerente e feed si aggiornano via WebSocket;
- alla chiusura crediti e prodotto vengono trasferiti atomicamente;
- il vincitore viene mostrato nella stanza e registrato negli storici.

L'ADMIN inserisce l'orario italiano (`Europe/Rome`); il Producer lo converte e
lo salva come istante UTC, evitando ambiguità legate all'ora legale.

## Prodotti e disponibilità

La tabella `prodotti` espone direttamente:

- `astabile`: indica in modo esplicito se l'ADMIN può usarlo in un'asta;
- `quantita_disponibile`: unità ancora acquistabili o programmabili;
- `quantita_bloccata`: unità già riservate da aste programmate o aperte.

Più aste dello stesso prodotto sono consentite soltanto se esiste una unità
disponibile per ciascuna asta. Programmare un'asta sposta una unità da
`quantita_disponibile` a `quantita_bloccata` nella stessa transazione.

## Portafoglio virtuale

Ogni utente possiede crediti finti. Il saldo è diviso in totale, riservato e
disponibile (`totale - riservato`). Se il saldo disponibile non copre una
puntata, l'offerta viene rifiutata. Quando cambia il leader, la nuova somma viene
riservata e quella del precedente leader viene liberata atomicamente.

## Architettura

```text
Browser ──HTML/REST──► Consumer :8082 ──REST/JWT──► Producer :8081
   │                                                │
   └──────── WebSocket + ticket breve ──────────────┤
                                                    ▼
                                               PostgreSQL
```

Il browser riceve le pagine dalla Consumer. Le operazioni normali passano dal
client REST della Consumer; la stanza d'asta apre un WebSocket diretto verso il
Producer usando un ticket monouso e a breve scadenza.

## Tecnologie previste

- Java 21 e Spring Boot 3.5
- Spring MVC, Spring Data JPA e Validation
- Spring Security
- Spring WebSocket con STOMP
- PostgreSQL e Flyway
- Thymeleaf e JavaScript nativo
- Docker Compose
- JUnit 5, Mockito e Testcontainers PostgreSQL

## Funzionalità previste

- registrazione, login, rinnovo dei token e logout;
- catalogo con disponibilità e indicatore `astabile`;
- acquisto a prezzo fisso;
- portafoglio virtuale e registro movimenti;
- programmazione delle aste riservata all'ADMIN;
- lobby e stanza pre-live;
- offerte live con controllo della concorrenza;
- assegnazione del prodotto al vincitore;
- storico personale delle vittorie per l'utente;
- storico globale di aste e vincitori per l'ADMIN;
- modulo email riepilogativa dopo la chiusura, già presente; invio SMTP esterno fuori scope;
- chiusura automatica e recupero dopo un riavvio.

## Contratti principali

Swagger UI del Producer: [http://localhost:8081/swagger-ui.html](http://localhost:8081/swagger-ui.html).
Il JSON OpenAPI è disponibile su `/v3/api-docs` e documenta gli endpoint implementati.
Usare **Authorize** con un access token per le API protette.

Probe pubbliche nel package `monitoring`: `GET /api/v1/health` verifica la liveness;
`GET /api/v1/ready` verifica anche la disponibilità del database e la readiness
applicativa. Rispondono `200` se disponibili, `503` altrimenti.

Base REST: `http://localhost:8081/api/v1`

La tabella seguente include anche contratti futuri. Non sono implementati
acquisti fissi e API inventario; lo stato di ogni rotta è
indicato nella [tabella REST](docs/04-api-rest.md#rest).

| Metodo | Endpoint | Scopo |
| --- | --- | --- |
| `POST` | `/auth/register` | registra un utente |
| `POST` | `/auth/login` | apre una sessione applicativa |
| `POST` | `/auth/refresh` | rinnova i token della sessione |
| `POST` | `/auth/logout` | revoca la sessione corrente |
| `DELETE` | `/me` | disattiva e anonimizza il proprio account |
| `GET` | `/prodotti` | catalogo filtrabile |
| `POST` | `/prodotti/{id}/acquisti` | acquisto a prezzo fisso |
| `GET` | `/me/inventario` | prodotti vinti o acquistati |
| `GET` | `/me/portafoglio` | saldo e movimenti paginati |
| `PUT` | `/me/portafoglio/impostazioni` | imposta saldo virtuale USER |
| `POST` | `/admin/aste` | programma un'asta e riserva lo stock |
| `GET` | `/aste` | lobby delle aste |
| `GET` | `/aste/{id}` | snapshot autorevole |
| `POST` | `/aste/{id}/ticket` | ticket WebSocket monouso |
| `GET` | `/me/vittorie` | storico personale delle vittorie |
| `GET` | `/admin/aste/storico` | storico globale per l'ADMIN |

WebSocket Producer: `ws://localhost:8081/ws`

- comandi: `/app/aste/{id}/join`, `/app/aste/{id}/offerte`;
- eventi stanza: `/topic/aste/{id}`;
- errori privati: `/user/queue/aste`.

Il contratto completo è in [docs/04-api-rest.md](docs/04-api-rest.md).

## Database

Le due tabelle centrali del motore live sono:

| Tabella | Responsabilità |
| --- | --- |
| `aste` | programmazione, prezzo iniziale, timer, stato, vincitore e prezzo finale |
| `offerte` | storico immutabile dei rilanci accettati |

Sono affiancate da `utenti`, `auth_sessions`, `portafogli`, `movimenti_portafoglio`, `prodotti`,
`categorie` e `inventario_utenti`. Schema e DDL sono descritti in
[docs/03-database.md](docs/03-database.md).

Le migrazioni Flyway del Producer creano una tabella per file, senza inserire
dati iniziali. Per provare il modulo auth, registra un utente tramite
`POST /auth/register`. Per ottenere un account ADMIN in locale, promuovi un
utente già registrato nel database con
`UPDATE utenti SET ruolo = 'ADMIN' WHERE username = 'nome_utente';`.
Non usare questa procedura come funzione di gestione degli utenti in produzione.
Se il database locale ha già applicato le vecchie migrazioni V1/V2, usa un
database nuovo prima di riavviare il Producer: la cronologia Flyway precedente
non corrisponde più ai file attuali. Conserva una copia degli eventuali dati
che vuoi mantenere.

La registrazione crea anche un portafoglio a zero. Il login restituisce un
access token Bearer valido 30 minuti e un refresh token valido 7 giorni.
Il logout revoca subito entrambi. Con il profilo `prod`, impostare
`AUTH_JWT_SECRET` in `.env` con una stringa casuale di almeno 32 byte: senza
questa chiave il Producer non si avvia.

## Struttura repository

```text
.
├── producer/
├── consumer/
├── docs/
├── compose.yaml
├── .env.example
├── Consegna.md
└── pom.xml
```

## Build

Dalla radice del repository, per verificare Producer e Consumer.

Windows:

```powershell
.\producer\mvnw.cmd -f pom.xml test
```

macOS/Linux:

```sh
./producer/mvnw -f pom.xml test
```

## Avvio Docker

Solo PostgreSQL per lo sviluppo:

```powershell
docker compose up -d postgres
```

Poi avviare `LiveAuctionProducerApplication` da IntelliJ con il normale pulsante
Run, selezionando un JDK 21 per il progetto e la run configuration. Il Producer
usa `localhost:5432`, applica le migrazioni Flyway e ascolta su
`localhost:8081`. L'avvio automatico di Docker Compose da Spring Boot è
disabilitato, così funziona anche se IntelliJ usa la radice del repository come
directory di lavoro. In Postman selezionare l'ambiente **LiveAuction • Locale**
prima di inviare le richieste.

Avviare poi `LiveAuctionConsumerApplication` con JDK 21: la UI è su
`http://localhost:8082` e chiama per default
`http://localhost:8081/api/v1`. Per URL diversi impostare
`LIVEAUCTION_PRODUCER_URL` e `LIVEAUCTION_PRODUCER_WS_URL`; il secondo URL deve
essere raggiungibile dal browser e l'origine WebSocket va ammessa dal Producer.
Registrare un USER dalla UI; per provare le pagine ADMIN in locale, promuovere
un account come indicato nella sezione Database e ripetere il login.
Per rotte e funzionalità mancanti vedere [Interfaccia e flussi](docs/05-ui-flussi.md).

Intero stack con profilo `prod`:

```powershell
docker compose --profile prod up --build -d
docker compose --profile prod ps
```

Se `5432` è occupata, copiare `.env.example` in `.env` e impostare
`POSTGRES_PORT=5433`; in quel caso impostare anche
`DB_URL=jdbc:postgresql://localhost:5433/liveauction` nella run configuration
di IntelliJ. Arresto senza cancellare i dati:

```powershell
docker compose --profile prod down
```

## Documentazione

- [Consegna aggiornata](Consegna.md)
- [Indice progettazione](docs/README.md)
- [Requisiti e regole](docs/01-requisiti.md)
- [Architettura](docs/02-architettura.md)
- [Database PostgreSQL](docs/03-database.md)
- [REST e WebSocket](docs/04-api-rest.md)
- [Collection Postman](docs/postman/README.md)
- [Interfaccia e flussi](docs/05-ui-flussi.md)
- [Roadmap e test](docs/06-piano-lavoro.md)
- [Motore LiveAuction](docs/07-liveauction.md)
- [Offerte e chiusura: integrazione](docs/08-offerte-chiusura.md)
- [Portafoglio e ledger](docs/09-portafoglio.md)
- [Notifiche email](docs/10-notifiche-email.md)
