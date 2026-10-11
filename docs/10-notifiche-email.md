# Email al vincitore — modulo Maikol

Modulo integrato in `main` nel commit `c9e2a11`; stato aggiornato al
10 ottobre 2026. Il modulo non chiude aste né modifica saldi,
ledger o inventario. Non aggiunge endpoint REST/STOMP.

Nel branch `web_socket`, dal 10 ottobre 2026, sono aggiunti template SMTP,
controlli di configurazione e test end-to-end con SMTP locale via TCP reale.
Queste ultime modifiche devono ancora essere integrate in `main`.

## Flusso e dipendenze

- `EmailQueueService` ascolta `AUCTION_CLOSED`, già pubblicato dopo il commit.
  Scrive in una transazione `REQUIRES_NEW`, senza un secondo listener AFTER_COMMIT.
- V11 crea `notifiche_email`: una sola richiesta per asta (PK `asta_id`), stati
  PENDING/SENT/SKIPPED, tentativi, prossimo tentativo UTC, istante di invio e
  codice tecnico dell'errore. Non conserva copie di indirizzi email o del corpo.
- `EmailJob` recupera le richieste mancanti anche all'avvio e seleziona aste
  CHIUSA con vincitore e `chiusa_at` valorizzato. Questo recupera eventi persi
  fra il commit e il listener, senza ripetere il settlement.
- Un executor dedicato esegue l'invio fuori dal thread della chiusura e dai job
  delle aste. Ogni tentativo usa una transazione distinta, con lock della sola
  riga di coda e `SKIP LOCKED`, così worker concorrenti non elaborano la stessa
  notifica contemporaneamente. SMTP ha timeout di connessione/lettura/scrittura
  di 5 secondi.
- Destinatario e prodotto sono letti dal database al tentativo. Un utente non
  più attivo viene marcato SKIPPED: non si invia all'indirizzo anonimizzato.
- L'email contiene nome prodotto, ID asta, prezzo vincente in CRD e data di
  conclusione visualizzata in Europe/Rome; la persistenza resta UTC.
- Un errore SMTP lascia PENDING e pianifica il retry (60 secondi predefiniti,
  senza limite di tentativi). In database/log viene scritto solo un codice
  generico e l'ID asta, non il messaggio del provider, password o token.

Gli invii ripetuti di un evento e i recuperi non riaprono richieste SENT/SKIPPED.
**Limite SMTP:** se il provider accetta l'email e il processo si interrompe prima
del commit SENT, il retry può inviarla di nuovo. La consegna è almeno-una-volta,
non exactly-once; il lock impedisce invii simultanei, non questa finestra di crash.

## Configurazione

L'invio è **disabilitato per default**. Gli eventi possono comunque registrare
richieste PENDING. Non vengono effettuate connessioni SMTP finché non si abilita
il modulo. Per attivarlo servono:

| Variabile | Significato/default |
| --- | --- |
| `EMAIL_ENABLED` | `true` per attivare job e invio, default `false` |
| `EMAIL_FROM` | indirizzo del mittente, obbligatorio quando abilitato |
| `SMTP_HOST` | host SMTP, obbligatorio quando abilitato |
| `SMTP_PORT` | porta, default `587` |
| `SMTP_USERNAME`, `SMTP_PASSWORD` | credenziali esterne, mai committate |
| `SMTP_AUTH` | default `true`; false solo se il server non richiede autenticazione |
| `SMTP_STARTTLS` | default `true`; false solo per un server di prova appropriato |
| `SMTP_STARTTLS_REQUIRED` | default uguale a `SMTP_STARTTLS`; rifiuta il server se non offre STARTTLS |
| `SMTP_SSL` | TLS implicito, default `false`; richiede STARTTLS e REQUIRED entrambi false |
| `EMAIL_INTERVAL_MS` | pausa fra cicli, default `30000`, minimo `1000` |
| `EMAIL_BATCH_SIZE` | massimo tentativi/recuperi per ciclo, default `20`, da 1 a 100 |
| `EMAIL_RETRY_SECONDS` | attesa dopo errore SMTP, default `60`, minimo `1` |

Anche il servizio Producer nel profilo Docker `prod` riceve queste variabili.
Configurazione abilitata senza host/mittente causa un errore di avvio esplicito.
Con `SMTP_AUTH=true`, username e password sono obbligatori; una porta fuori
da 1–65535 o TLS implicito insieme a STARTTLS causano un errore di avvio.
Le credenziali non vengono incluse nel messaggio di errore.
Prima di abilitare SMTP reale controllare le richieste arretrate: il recupero
include **tutte** le aste già concluse con vincitore, anche prima dell'installazione
del modulo. Per una prova usare un destinatario/server di test, non utenti reali.

### Attivazione nell'ambiente concordato

1. Copiare `.env.example` in `.env` (ignorato da Git) e configurare host,
   mittente e credenziali del provider scelto dal team. Lasciare
   `EMAIL_ENABLED=false` durante la preparazione.
2. Per SMTP con STARTTLS (tipicamente porta 587), usare `SMTP_STARTTLS=true`,
   `SMTP_STARTTLS_REQUIRED=true`, `SMTP_SSL=false`. Per TLS implicito
   (tipicamente porta 465), usare `SMTP_SSL=true`, `SMTP_STARTTLS=false`,
   `SMTP_STARTTLS_REQUIRED=false`. Verificare i parametri del provider.
3. Controllare le richieste PENDING e le aste concluse senza notifica prima
   dell'attivazione: il job può recuperare anche vittorie precedenti.
   Effettuare la prima prova su un database dedicato e con destinatari di test.
4. Impostare `EMAIL_ENABLED=true` e riavviare il Producer. Docker Compose
   passa i valori di `.env` al servizio `producer`; l'avvio da IDE **non** carica
   automaticamente `.env`: inserire le stesse variabili nella run configuration.
   Per Docker `prod` resta obbligatoria anche `AUTH_JWT_SECRET`.
5. Verificare, dopo la scadenza, `AUCTION_CLOSED`, stato CHIUSA e richiesta
   email SENT. Un errore SMTP deve lasciare PENDING con `SMTP_ERROR` e retry
   futuro, senza ripetere addebiti o assegnazione. Mai condividere dump delle
   variabili d'ambiente o dei token per diagnosticare un errore SMTP.

I server locali senza TLS/autenticazione sono utilizzabili soltanto per prove
isolate: `SMTP_AUTH=false`, `SMTP_STARTTLS=false`,
`SMTP_STARTTLS_REQUIRED=false`, `SMTP_SSL=false`. Non usare questi valori per
inviare credenziali a un server remoto. Il test automatizzato avvia da sé un
server in loopback su porta casuale, che non inoltra alcuna email all'esterno.

## Cosa manca fuori dal modulo

La chiusura automatica è collegata allo scheduler e integrata in `main`
dal merge `3bcc9cc`. Il modulo email è già
collegato al servizio di chiusura ed è testabile senza SMTP reale. Il provider
SMTP e le sue credenziali devono essere configurati dal team, poi va verificato
il flusso completo dopo la chiusura automatica. Il frontend è di Andrea.

La configurazione e i test locali non costituiscono l'attivazione del provider
del team: host, porta, mittente e credenziali reali restano una scelta esterna.

## Verifiche

Test con PostgreSQL di prova e SMTP simulato: contenuto del riepilogo,
configurazione, accodamento post-commit, rollback senza notifica, idempotenza,
errore e retry persistiti, recupero di un evento perso, asta senza vincitore,
utente disattivato, concorrenza e comportamento del job.
I fixture vengono rimossi dopo ogni test. Nessuna email è stata inviata a
provider o destinatari esterni.

La suite WebSocket del branch `web_socket` usa un `JavaMailSender` reale e un
server SMTP locale via TCP, senza mock del client email. Copre comando STOMP,
rilancio, chiusura tramite `AstaScheduler`, evento pubblico, ledger/inventario,
accodamento post-commit e ricezione del messaggio MIME con destinatario,
prodotto e importo. Verifica anche assenza di duplicati, errore SMTP e retry
senza nuovo settlement, chiusura senza vincitore e rifiuto dell'invio in chiaro
quando STARTTLS è obbligatorio. Scheduler e worker sono invocati esplicitamente
per non dipendere da attese temporali o interferire con fixture di altre suite.
Non verifica il provider esterno, i suoi certificati TLS o la consegna in inbox.

Verifica del branch `web_socket` del 10 ottobre 2026: **235 test Producer e
1 test Consumer superati**, senza fallimenti, errori o test saltati, con
`RUN_DB_TESTS=true`, `RUN_WS_TESTS=true`, `ASTE_SCHEDULER_ENABLED=false` e
PostgreSQL 16 dedicato. Il server SMTP di test ascolta soltanto in loopback.
Configurazione Compose validata, inclusa la disattivazione coerente di
STARTTLS e del requisito STARTTLS per server locali. Nessuna nuova migrazione.

Verifica completa riportata l'8 ottobre 2026 sul branch di sviluppo:
**200 test Producer superati, 0 fallimenti, 0 errori,
0 saltati**, inclusi PostgreSQL, WebSocket e SMTP simulato:

```sh
RUN_DB_TESTS=true RUN_WS_TESTS=true ASTE_SCHEDULER_ENABLED=false \
DB_URL=jdbc:postgresql://localhost:5433/liveauction_email_verified_20261008 \
sh producer/mvnw -f producer/pom.xml clean test
```

Il database è dedicato ai test; i job automatici delle aste sono disattivati
durante la suite, i test del ciclo di vita li richiamano esplicitamente.
Nel report dell'8 ottobre è riportata anche la validazione della configurazione
Docker Compose.

Verifica del 9 ottobre 2026 su `main` (`c9e2a11`): build di Producer e Consumer
riuscita, 92 test superati e 82 saltati, senza fallimenti o errori. I test
PostgreSQL e WebSocket non sono stati eseguiti in questa verifica. Con la JVM
locale Java 25 è stato necessario caricare Mockito come agente all'avvio;
il progetto resta configurato per Java 21:

```sh
./producer/mvnw -f pom.xml test \
  -DargLine=-javaagent:$HOME/.m2/repository/org/mockito/mockito-core/5.17.0/mockito-core-5.17.0.jar
```
