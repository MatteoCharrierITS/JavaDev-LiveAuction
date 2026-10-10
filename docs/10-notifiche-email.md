# Email al vincitore — modulo Maikol

Modulo integrato in `main` nel commit `c9e2a11`; stato aggiornato al
10 ottobre 2026. Il modulo non chiude aste né modifica saldi,
ledger o inventario. Non aggiunge endpoint REST/STOMP.

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
| `EMAIL_INTERVAL_MS` | pausa fra cicli, default `30000`, minimo `1000` |
| `EMAIL_BATCH_SIZE` | massimo tentativi/recuperi per ciclo, default `20`, da 1 a 100 |
| `EMAIL_RETRY_SECONDS` | attesa dopo errore SMTP, default `60`, minimo `1` |

Anche il servizio Producer nel profilo Docker `prod` riceve queste variabili.
Configurazione abilitata senza host/mittente causa un errore di avvio esplicito.
Prima di abilitare SMTP reale controllare le richieste arretrate: il recupero
include **tutte** le aste già concluse con vincitore, anche prima dell'installazione
del modulo. Per una prova usare un destinatario/server di test, non utenti reali.

## Cosa manca fuori dal modulo

La chiusura automatica è collegata allo scheduler e integrata in `main`
dal merge `3bcc9cc`. Il modulo email è già
collegato al servizio di chiusura ed è testabile senza SMTP reale. Il provider
SMTP e le sue credenziali devono essere configurati dal team, poi va verificato
il flusso completo dopo la chiusura automatica. Il frontend è di Andrea.

## Verifiche

Test con PostgreSQL di prova e SMTP simulato: contenuto del riepilogo,
configurazione, accodamento post-commit, rollback senza notifica, idempotenza,
errore e retry persistiti, recupero di un evento perso, asta senza vincitore,
utente disattivato, concorrenza e comportamento del job.
I fixture vengono rimossi dopo ogni test. Non sono state inviate email reali.

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
