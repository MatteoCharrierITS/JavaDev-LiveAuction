# Istruzioni per lavorare su LiveAuction

## Contesto

- Progetto didattico Java 21, Spring Boot 3.5, PostgreSQL e Flyway.
- `producer/` espone REST e WebSocket sulla porta 8081 e possiede il database e
  tutta la logica di dominio.
- `consumer/` espone la UI Thymeleaf sulla porta 8082 e comunica con il Producer
  tramite le sue API; non accede direttamente al database.
- `README.md` indica le aree e le persone assegnate. Non attribuire aree ancora
  libere senza una decisione del team.

## Prima di modificare il codice

- Leggi il contratto pertinente in `docs/`; `docs/04-api-rest.md` descrive gli
  endpoint, `docs/03-database.md` lo schema e `docs/01-requisiti.md` le regole.
- Verifica quali endpoint sono implementati: le collection in `docs/postman/`
  includono anche richieste per moduli ancora in sviluppo.
- Mantieni le modifiche nel package del dominio interessato e coordina i
  cambiamenti alle interfacce condivise con gli altri moduli.

## Convenzioni del progetto

- Base REST del Producer: `/api/v1`. Usa DTO e validazione Jakarta per input e
  output; non esporre entity JPA o hash delle password nelle risposte.
- Gli errori REST seguono `application/problem+json`; rispetta gli status e i
  codici definiti nel contratto quando il modulo li specifica.
- I ruoli sono `USER` e `ADMIN`. La registrazione crea un utente `USER` e un
  portafoglio con credito iniziale configurato in `app.portafoglio.saldo-iniziale`
  (default 10.000 CRD) e movimento ledger nella stessa transazione. Il wallet è
  di sola lettura per l'utente, senza API di ricarica. Il login restituisce un
  token Bearer; il Consumer lo conserva nella propria sessione server-side.
- Il Producer abilita `@PreAuthorize` con `@EnableMethodSecurity`. Per le nuove
  operazioni protette, definisci il ruolo richiesto sia nel contratto REST sia
  sul metodo di servizio quando la regola riguarda l'operazione di dominio.
- I `GET` sotto `/prodotti/**` e `/aste/**` sono pubblici. Prima di aggiungere
  una lettura riservata in questi percorsi, inserisci una regola HTTP più
  specifica che preceda quella pubblica.
- Non registrare nei log password o token. Nel profilo `prod`, la chiave JWT
  arriva da `AUTH_JWT_SECRET`, mai dal codice o da un file `.env` committato.
- Usa `BigDecimal` per importi e crediti. Le modifiche al saldo richiedono un
  movimento di ledger; il WebSocket trasporta eventi e non valida offerte.
- Conserva gli istanti persistiti in UTC. Interpreta l'input di programmazione
  delle aste nel fuso `Europe/Rome` prima della conversione.
- Lo schema è gestito da Flyway in `producer/src/main/resources/db/migration/`;
  Hibernate usa `ddl-auto=validate`. Aggiungi nuove migrazioni versionate invece
  di modificare quelle già applicate in ambienti condivisi.
- Le migrazioni creano una tabella per file e non inseriscono dati demo.

## Verifiche

- Dalla radice: `./producer/mvnw.cmd -f producer/pom.xml test` su Windows; su
  sistemi Unix usa `./producer/mvnw -f producer/pom.xml test`.
- I test di integrazione che richiedono PostgreSQL si attivano con
  `RUN_DB_TESTS=true` e una `DB_URL` verso un database di prova.
- Per avviare PostgreSQL locale: `docker compose up -d postgres`. Non usare
  `docker compose down -v` su un volume che contiene dati da conservare.
- Aggiorna documentazione e collection Postman quando cambi un contratto API.
