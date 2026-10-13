# Verifica e allineamento della documentazione

Revisione del 12 ottobre 2026 sulla base `main` `4b82f1e`.
Questo report conserva la verifica precedente alle modifiche di Marco:
annullamento e storici sono ora completati localmente e verificati nel
[report successivo](12-annullamento-storici.md); le indicazioni sotto relative
alle sue attività mancanti descrivono lo stato della base precedente.
Le assegnazioni restano quelle del README: ogni responsabile cura anche
documentazione e Postman della propria porzione. Il TODO è aggiornato con
attività per persona e stato in tabella. L'attivazione SMTP esterna è fuori scope
per la consegna, secondo la decisione dell'utente del 12 ottobre.

## Fonti e perimetro

Confrontati README, consegna, documenti 01–10, report storico WebSocket e
collection Postman con controller, DTO, servizi, sicurezza, scheduler,
configurazione SMTP, migrazioni Flyway e struttura del Consumer.
Il codice disponibile in `main` è la fonte dello stato implementato. Il lavoro
non pubblicato dai colleghi non è deducibile da questa verifica.
I documenti dei requisiti e della UI descrivono anche il risultato finale atteso.

## Correzioni

- Chiusura automatica periodica e al riavvio già integrata: eliminati i riferimenti
  che la indicavano ancora da sviluppare nell'architettura.
- Template SMTP, controlli STARTTLS/TLS implicito e suite SMTP locale già in
  `main` (`4b82f1e`); l'attivazione del provider esterno è esclusa dallo scope.
- Tabella REST con stato per ogni endpoint; inventario, acquisti fissi,
  annullamento e due storici sono contratti previsti, senza controller.
  Indicati i dettagli contrattuali ancora da decidere senza inventare DTO o regole.
- UI e flussi Consumer identificati come progettazione, non pagine disponibili.
  I filtri lobby «in partenza» e «a cui partecipo» non hanno API dedicate.
- Presenza temporanea via STOMP, senza `sequence`; lo snapshot REST espone
  leader e vincitore mascherati, non lista e numero dei presenti.
- Nessun evento separato `TIMER_EXTENDED`: il nuovo timer è in `BID_ACCEPTED`.
  `AUCTION_CANCELLED` resta previsto con l'annullamento.
- Precisati ordine dei lock, retry idempotente dei comandi e retry dello scheduler.
- Precisata la distinzione fra migrazioni di creazione e V10 di evoluzione delle
  offerte; le migrazioni applicate non sono state modificate.
- Conservati i risultati storici con data e contesto, senza presentarli come
  verifiche rieseguite oggi. Il report WebSocket resta un documento storico.

## Catalogo di Cristian e Postman

Verificati tutti i dieci endpoint implementati di categorie e prodotti:
URL/metodi, accesso pubblico/ADMIN, `@PreAuthorize`, body e DTO, `versione`,
stock disponibile/bloccato, filtri, paginazione, ordinamento, visibilità e codici
catalogo corrispondono al contratto. Nessuna modifica al codice del catalogo.

La collection già conservava ID, versione e body completo per il PUT, ma molte
richieste non avevano assert espliciti. Aggiunti controlli di status e DTO,
paginazione, visibilità pubblica e valori restituiti dalle scritture.
Dichiarate nell'ambiente le variabili generate; marcate FUTURO le API mancanti
nelle collection aste e inventario. Le istruzioni del runner escludono i
contratti futuri e separano i casi negativi dal percorso standard.

Controllati parsing dei JSON, sintassi degli script, sostituzione delle variabili
nei body e corrispondenza delle richieste con controller e DTO.
Verificati 33 riferimenti REST nelle collection, 29 script JavaScript,
15 body dopo sostituzione delle variabili e 69 link locali Markdown.
`git diff --check` è superato.
Il runner Postman non è stato eseguito; non è una prova HTTP end-to-end.
La verifica documentale richiesta a Cristian nel vecchio TODO è completata
con questa revisione ed è indicata con una spunta nel TODO.

## Build locale del 12 ottobre

La verifica precedente in questa chat ha compilato Producer e Consumer:
**96 test Producer e 1 test Consumer superati; 104 test Producer saltati**,
zero fallimenti o errori nell'esecuzione riuscita. PostgreSQL e WebSocket non
sono abilitati, quindi questo risultato non verifica database, STOMP o SMTP.
La prima esecuzione era fallita per l'aggancio Mockito alla JVM locale Java 25;
caricando esplicitamente l'agente è riuscita:

```sh
./producer/mvnw -f pom.xml test \
  -DargLine=-javaagent:$HOME/.m2/repository/org/mockito/mockito-core/5.17.0/mockito-core-5.17.0.jar
```

Il progetto resta configurato per Java 21. Questa revisione modifica solo
Markdown e collection JSON: non riesegue la suite di dominio né invia email.
I conteggi completi riportati il 7–10 ottobre rimangono risultati storici;
per una nuova verifica completa servono un database dedicato, `RUN_DB_TESTS=true`
e `RUN_WS_TESTS=true`, con scheduler in background disabilitato durante i test.

## Decisioni ancora necessarie

- Il team deve scegliere il referente delle API inventario e acquisti fissi.
- Marco deve completare il contratto di annullamento e storici prima dello
  sviluppo, compresa la gestione dell'annullamento in presenza di offerte.
- Il rate limit indicativo del motore live e i filtri UI senza API sono proposte
  da confermare nel team, non nuove attività obbligatorie assegnate ai colleghi.
