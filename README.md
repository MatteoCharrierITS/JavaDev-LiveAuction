# LiveAuction

Marketplace didattico con **aste live**. Un amministratore gestisce il catalogo
e programma le aste; gli utenti entrano nella stanza, rilanciano in tempo reale
con crediti virtuali e il vincitore riceve il prodotto nel proprio inventario.
Si possono anche comprare i prodotti a prezzo fisso.

Progetto di gruppo del corso di Software Development dell'ITS ICT Torino.
Il testo dell'esercitazione è in [Consegna.md](Consegna.md).

## In breve

| | |
| --- | --- |
| **Producer** (`:8081`) | API REST, WebSocket/STOMP della stanza, sicurezza JWT, logica di dominio, PostgreSQL + Flyway |
| **Consumer** (`:8082`) | Interfaccia web Thymeleaf; parla solo con le API del Producer, mai con il database |
| **Stack** | Java 21, Spring Boot 3.5, Spring Security, Spring WebSocket, JPA/JDBC, PostgreSQL 16, Docker Compose |
| **API** | Swagger UI su `http://localhost:8081/swagger-ui.html`, OpenAPI su `/v3/api-docs` |
| **Test** | JUnit 5 e Mockito (Producer e Consumer), `node --test` per il JavaScript della stanza |

```text
Browser ──HTML──► Consumer :8082 ──REST + JWT──► Producer :8081 ──► PostgreSQL
   │                                                  ▲
   └────────── WebSocket/STOMP con ticket monouso ─────┘
```

Il browser riceve le pagine dal Consumer, che conserva il token nella propria
sessione server-side: il JWT non arriva mai al browser. Solo la stanza live apre
un WebSocket diretto verso il Producer, con un ticket monouso e di breve durata
chiesto dal Consumer.

## Come funziona un'asta

1. L'ADMIN sceglie un prodotto **astabile**, data e ora (fuso `Europe/Rome`,
   salvate in UTC) e il prezzo iniziale. Una unità del prodotto viene bloccata
   subito: senza stock disponibile l'asta non si programma.
2. La stanza apre **3 minuti prima** dell'inizio: si entra e si vedono i
   partecipanti, ma non si offre ancora.
3. L'asta dura **7 minuti**; ogni offerta valida aggiunge **20 secondi** alla
   scadenza. Il tempo lo decide sempre il server, il browser lo interpola.
4. Chi è in testa ha i crediti **riservati**; quando viene superato gli tornano
   disponibili nella stessa transazione.
5. Alla scadenza lo scheduler chiude l'asta: crediti all'incasso e prodotto
   all'inventario del vincitore, in modo atomico. Senza offerte l'unità torna in
   stock.

Ogni movimento di crediti passa da un registro (ledger); offerte, chiusura e
acquisti bloccano le righe coinvolte per evitare doppie vittorie o stock
negativo. Le regole complete sono in [Requisiti e regole](docs/01-requisiti.md)
e nel [motore LiveAuction](docs/07-liveauction.md).

## Avvio

Servono **Java 21** e **Docker**. Si parte dalla radice del repository.

### Sviluppo (IDE)

```sh
docker compose up -d postgres
```

Poi si avviano da IntelliJ `LiveAuctionProducerApplication` (porta 8081, applica
le migrazioni Flyway) e `LiveAuctionConsumerApplication` (porta 8082). La UI è su
<http://localhost:8082>.

### Stack completo in Docker

```sh
cp .env.example .env    # impostare almeno AUTH_JWT_SECRET (32+ caratteri casuali)
docker compose --profile prod up --build -d
```

Il profilo `prod` usa cookie `Secure`: su HTTP con un IP diverso da localhost
impostare `CONSUMER_SESSION_COOKIE_SECURE=false`. Per la demo su più PC della
stessa rete c'è `scripts/avvia-locale.sh` (macOS), spiegato nella
[guida alla prova](docs/19-prova-professore.md).

### Primo accesso

- Ogni utente registrato è `USER` e riceve **10.000 CRD** una volta sola
  (`WALLET_INITIAL_BALANCE`).
- Un `ADMIN` si ottiene promuovendo un utente già registrato:
  `UPDATE utenti SET ruolo = 'ADMIN' WHERE username = '...';` e rifacendo il login.
- Gli acquisti a prezzo fisso e l'incasso delle aste vanno all'ADMIN indicato da
  `SHOP_ADMIN_ID`; con il valore `0` gli acquisti restano disabilitati e ogni
  asta incassa sull'ADMIN che l'ha programmata.
- L'ADMIN può entrare nella stanza di un'asta come osservatore: vede tutto in
  tempo reale ma non offre e non compare tra i partecipanti.

Le altre variabili sono commentate in [.env.example](.env.example).

## Test

I test di integrazione vogliono un database dedicato, da creare una volta:

```sh
docker compose up -d postgres
docker compose exec postgres createdb -U postgres liveauction_test
```

```sh
# Producer e Consumer (macOS/Linux o Git Bash)
RUN_DB_TESTS=true RUN_WS_TESTS=true ASTE_SCHEDULER_ENABLED=false \
DB_URL=jdbc:postgresql://localhost:5432/liveauction_test \
DB_USERNAME=postgres DB_PASSWORD=postgres \
./producer/mvnw -f pom.xml verify

# JavaScript della lobby e della stanza
node --test consumer/src/test/js/*.test.*
```

In PowerShell le variabili si impostano prima, una per riga (`$env:RUN_DB_TESTS = "true"`
e così via), poi `.\producer\mvnw.cmd -f pom.xml verify`.

Senza `DB_URL` e `RUN_DB_TESTS` i test di integrazione vengono saltati e girano
solo quelli che non toccano il database. Usare sempre un database dedicato ai
test, mai quello della demo.

## Documentazione

Tutto sta in [`docs/`](docs/README.md). Da dove partire:

| Voglio… | Documento |
| --- | --- |
| capire cosa fa il sistema e con quali regole | [01 · Requisiti e regole di dominio](docs/01-requisiti.md) |
| vedere come sono divisi Producer e Consumer | [02 · Architettura e comunicazione](docs/02-architettura.md) |
| leggere lo schema del database | [03 · Modello dati PostgreSQL](docs/03-database.md) |
| usare le API (REST e STOMP) | [04 · Contratti REST e WebSocket](docs/04-api-rest.md), Swagger, [collection Postman](docs/postman/README.md) |
| vedere pagine e flussi dell'utente | [05 · Interfaccia e flussi](docs/05-ui-flussi.md) |
| capire il motore delle aste live | [07 · Motore LiveAuction](docs/07-liveauction.md), [08 · Offerte e chiusura](docs/08-offerte-chiusura.md) |
| capire crediti, riserve e ledger | [09 · Portafoglio e ledger](docs/09-portafoglio.md), [18 · Wallet di sola lettura](docs/18-wallet-sola-lettura.md) |
| annullamento e storici | [12 · Annullamento e storici](docs/12-annullamento-storici.md) |
| inventario e acquisti a prezzo fisso | [13 · Inventario e acquisti](docs/13-inventario-acquisti.md) |
| email al vincitore (pronta, SMTP fuori consegna) | [10 · Notifiche email](docs/10-notifiche-email.md) |
| avviare la demo su più PC | [19 · Prova su hotspot](docs/19-prova-professore.md), [14 · LAN/VPN](docs/14-avvio-demo-vpn.md), [15 · Multi-PC](docs/15-demo-multipc.md) |

Gli altri documenti numerati (06, 11, 16, 17, 20) e [todo.md](docs/todo.md) sono
il **diario del lavoro**: piano, verifiche, correzioni e decisioni nell'ordine in
cui sono avvenute. Raccontano come ci siamo arrivati; lo stato attuale è quello
descritto dai documenti qui sopra.

## Team

| Persona | Area |
| --- | --- |
| **Matteo** | Autenticazione e utenti: registrazione, login, token, ruoli; supporto al portafoglio |
| **Mondir** | Portafoglio e movimenti: saldo totale, riservato e disponibile, ledger dei movimenti, operazioni sui crediti condivise con le aste |
| **Andrea** | Consumer: client REST verso il Producer, sessione web, pagine Thymeleaf e stanza live |
| **Maikol** | WebSocket e notifiche: STOMP, ticket, presenza, eventi della stanza, email al vincitore |
| **Cristian** | Prodotti e catalogo: categorie, CRUD ADMIN, ricerca, paginazione, stock |
| **Marco** | Programmazione aste: creazione ADMIN, orari, blocco stock, scheduler, annullamento e storici |
| **Tommi** | Offerte e chiusura: rilanci, estensioni, concorrenza, vincitore, trasferimento di crediti e prodotto |

Ognuno ha curato anche DTO, validazioni, errori, migrazioni e test della propria
area.

## Uso dell'intelligenza artificiale

Il professore ci ha chiesto di indicare in percentuale quanta IA abbiamo usato.
Una percentuale non avrebbe senso, perché l'IA e noi abbiamo fatto lavori
diversi, non lo stesso lavoro in quote diverse. Diciamo invece chiaramente chi
ha fatto cosa:

- **Noi** abbiamo fatto la progettazione: l'idea del prodotto, le regole
  dell'asta, la divisione tra Producer e Consumer, i contratti REST e
  WebSocket, il modello dati, i flussi d'uso, la divisione del lavoro e le
  decisioni prese strada facendo.
- **L'IA** ha scritto la quasi totalità del codice, ci ha aiutato a fare debug
  e ha scritto gran parte della documentazione, a partire dalle nostre
  specifiche. Di codice scritto a mano, tra tutti, ce n'è poco, e non lo
  nascondiamo.

Il lavoro dell'IA lo abbiamo guidato, rivisto, provato e corretto noi; le
istruzioni che le davamo stanno in [AGENTS.md](AGENTS.md).
