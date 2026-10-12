# Consegna di Marco — annullamento e storici

Modifiche locali del 12 ottobre 2026 sulla base `main` `4b82f1e`.
Le attività di Marco nel [TODO](todo.md) sono completate nella copia di lavoro;
questo report non indica un merge o una pubblicazione su remoto.

## API consegnate

| Metodo | Endpoint | Ruolo | Risultato |
| --- | --- | --- | --- |
| POST | `/api/v1/admin/aste/{id}/annullamento` | ADMIN | annullamento idempotente senza offerte |
| GET | `/api/v1/me/vittorie` | USER | vittorie del solo utente del token |
| GET | `/api/v1/admin/aste/storico` | ADMIN | aste terminali con filtri combinati |

DTO, esempi, paginazione, ordinamento, filtri ed errori sono nel
[contratto REST e STOMP](04-api-rest.md#annullamento-admin).
La collection [aste.json](postman/aste.json) contiene le richieste implementate,
i controlli delle risposte e il retry dell'annullamento. Nell'ambiente Postman
`annullamentoEsito` conserva l'esito iniziale per confrontarlo con il retry.

## Regole e integrazione

Il requisito esistente permette il rilascio dello stock prima di ricevere
un'offerta. Si applica quindi la regola conservativa: PROGRAMMATA,
STANZA_APERTA o APERTA, prima di fineAt, senza alcuna offerta storica,
comprese quelle ritirate. Annullare con offerte è un conflitto, senza effetti
su portafogli, riserve o ledger. CHIUSA e aste scadute non sono annullabili.

Il lock segue asta → prodotto, compatibile con offerte e settlement.
Lo stock è restituito una volta; versioni e sequence cambiano nella stessa
transazione. La migrazione V12 aggiunge annullata_at e l'indice dello storico,
senza modificare le migrazioni precedenti.

Per Maikol: AUCTION_CANCELLED estende il tipo AstaTransizioneEvent, conservando
il payload delle transizioni già supportato dal relay. Nessuna modifica al
controller STOMP o al motore delle offerte. Il relay inoltra l'evento al topic
esistente. Il test su WebSocket reale verifica delivery dopo commit, assenza
dopo rollback, sequence e retry senza eventi duplicati. Il contratto completo
è disponibile per la verifica di Maikol nel passaggio di consegna.

Per Andrea: le tre API sono disponibili e le pagine restano assegnate al
Consumer. La stanza dovrà mostrare ANNULLATA e disabilitare i rilanci sul
nuovo evento. Le pagine di vittorie/storico possono usare direttamente i DTO
e i filtri documentati, con i token già conservati nella sessione server-side.

Inventario e acquisti fissi restano da assegnare; nessuna nuova assegnazione.

## Verifica eseguita

Java 21, PostgreSQL 16 in un cluster temporaneo dedicato su localhost:55432;
nessun uso del volume PostgreSQL del progetto. Gli eventi sono stati provati
su WebSocket reale e le email con SMTP locale di test, senza invii reali.

```sh
RUN_DB_TESTS=true RUN_WS_TESTS=true \
DB_URL=jdbc:postgresql://127.0.0.1:55432/postgres \
./producer/mvnw -f producer/pom.xml verify

./consumer/mvnw -f consumer/pom.xml verify
```

Nell'esecuzione locale JAVA_HOME e MAVEN_USER_HOME indicavano Java 21 e la
cache scaricati in una directory temporanea; il progetto non richiede quei
percorsi specifici. Usare un JDK 21 configurato e un proprio database di prova
per ripetere la verifica.

- Producer: **268 test**, zero fallimenti, errori o test saltati; build e JAR riusciti.
- Consumer: **1 test**, zero fallimenti, errori o test saltati; build e JAR riusciti.
- Nuovi casi: 28 test di integrazione di gestione, 4 test unitari di annullamento
  e una prova aggiuntiva su WebSocket nella suite esistente.
- Coperti stati e limite di scadenza, sicurezza HTTP/servizio, isolamento utenti,
  pagine vuote, ordinamento stabile, filtri combinati ed estremi inclusivi delle date,
  stock/versioni, rollback e concorrenza con annullamento, offerte e chiusura.
- JSON della collection e dell'ambiente validi; sintassi di 8 script JavaScript
  delle aste verificata con Node. Runner Postman non eseguito.
- `git diff --check` superato.
