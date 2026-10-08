# LiveAuction — Spring Boot Producer/Consumer

> **[DA FARE — attività e prossimi passi per ogni persona](docs/todo.md)**
>
> Il riferimento del team per cosa manca, priorità e dipendenze. Aggiornalo quando completi o integri un'attività.

## Assegnazioni

| Persona | Area e funzionalità |
| --- | --- |
| **Matteo** | Autenticazione e utenti: registrazione, login, token, ruoli e sicurezza. **Consumer/frontend:** client REST, sessione web, pagine Thymeleaf e stanza live. |
| **Maikol** | WebSocket e notifiche: STOMP, ticket, presenza, eventi delle stanze ed email al vincitore. |
| **Cristian** | Prodotti e catalogo: categorie, CRUD ADMIN, ricerca, paginazione e stock. |
| **Mondir** | Portafoglio e movimenti: saldi, ledger e integrazione delle operazioni sui crediti. |
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

> **Stato del branch corrente:** include l'infrastruttura Spring Boot/Docker, lo schema Flyway,
> l'autenticazione, il WebSocket con ticket, presenza, comando di rilancio e
> trasporto degli eventi post-commit, il
> catalogo REST (categorie, prodotti, gestione ADMIN di stock, filtri e
> paginazione) e la programmazione ADMIN delle aste con blocco atomico dello
> stock, apertura automatica, lobby e snapshot pubblici. I servizi di offerte e
> settlement sono implementati; mancano la chiusura automatica, l'attivazione SMTP dell'email,
> annullamento, storici e API dedicate di portafoglio/inventario. La
> Consumer è ancora uno scheletro senza pagine applicative: il flusso completo
> LiveAuction non è ancora disponibile. Stato dettagliato in
> [attività del team](docs/todo.md) e [stato del modulo aste](docs/README.md#stato-del-modulo-aste).

## La feature distintiva: LiveAuction

Avanzamento locale nel branch `web_socket`: implementati email al vincitore,
coda persistente e retry; invio disabilitato per default, nessun invio reale
durante i test. Configurazione e limiti in [Notifiche email](docs/10-notifiche-email.md).

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
- email riepilogativa al vincitore dopo la chiusura;
- chiusura automatica e recupero dopo un riavvio.

## Contratti principali

Swagger UI del Producer: [http://localhost:8081/swagger-ui.html](http://localhost:8081/swagger-ui.html).
Il JSON OpenAPI è disponibile su `/v3/api-docs` e documenta gli endpoint implementati.
Usare **Authorize** con un access token per le API protette.

Probe pubbliche nel package `monitoring`: `GET /api/v1/health` verifica la liveness;
`GET /api/v1/ready` verifica anche la disponibilità del database e la readiness
applicativa. Rispondono `200` se disponibili, `503` altrimenti.

Base REST: `http://localhost:8081/api/v1`

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
| `GET` | `/me/portafoglio` | saldo e movimenti |
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
.\producer\mvnw.cmd -f producer/pom.xml test
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
