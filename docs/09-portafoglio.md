# Portafoglio e ledger

Modulo integrato in `main` il 9 ottobre 2026, a partire dalla base `c9e2a11`.
Matteo è subentrato sul modulo di Mondir.
Il contratto delle API è in [REST](04-api-rest.md#portafoglio).

## API e persistenza

- `GET /api/v1/me/portafoglio`: saldi e pagina dei movimenti personali,
  ordinati per data e ID decrescenti; lettura `REPEATABLE_READ` per avere saldo,
  conteggio e pagina coerenti durante modifiche concorrenti.
- `PUT /api/v1/me/portafoglio/impostazioni`: saldo virtuale assoluto, da zero
  a `9999999999.99`, massimo due decimali. Mantiene la riserva corrente e
  rifiuta un totale inferiore al riservato con `409 SALDO_INFERIORE_AL_RISERVATO`.
- Entrambe richiedono USER; ruolo e identità sono verificati anche nei servizi.
  Il Consumer può usare queste API con il token conservato nella sessione.

Il modulo usa JDBC su `portafogli` e `movimenti_portafoglio` delle migrazioni
V2/V8. Le proiezioni e i DTO sostituiscono i modelli JPA incompatibili presenti
nel precedente branch `portafoglio-movimenti`: non vengono introdotte tabelle
`wallet` o `movimenti`, né colonne `wallet_id`, `offerta_id` o `descrizione`.
Il ledger usa `portafoglio_id`, `asta_id`, `data_movimento`, i due saldi
successivi e l'enum `TipoMovimento` corrispondente alla V8. Le date sono `Instant`
UTC e gli importi `BigDecimal`. Nessuna migrazione è stata modificata o aggiunta.

L'impostazione del saldo registra un movimento con importo positivo pari alla
differenza assoluta, `asta_id` null e saldi successivi. Una riduzione resta un
movimento `IMPOSTAZIONE_SALDO`: il saldo successivo descrive il risultato.
Ripetere un PUT con lo stesso totale non produce una modifica, quindi non
inserisce importi zero e non cambia versione o data del portafoglio.

## Operazioni economiche condivise

`PortafoglioRepository` centralizza `bloccaPortafogli` e `movimenta`, estratti
senza duplicare lo SQL del motore d'asta. Le operazioni richiedono una transazione
esistente (`MANDATORY`), così saldo e ledger partecipano alla transazione
chiamante. Un errore annulla entrambe le scritture.

`OfferteRepository` mantiene le deleghe usate da rilanci, ritiri e settlement,
convertendo gli errori del portafoglio in `OffertaException` per conservare il
contratto STOMP. `PortafoglioBloccato` è ora una proiezione del modulo portafoglio.
Il tipo di movimento condiviso è un enum; l'adattatore delle aste accetta ancora
le stringhe dei servizi esistenti.

I lock conservano l'ordine del motore: utente, aste quando richieste, poi
portafogli ordinati per ID. L'impostazione del saldo acquisisce il lock utente
`FOR NO KEY UPDATE`, poi il portafoglio; non acquisisce lock sulle aste.
Non acquisire lock utente o asta dopo avere bloccato un portafoglio. Il settlement
conserva il proprio ordine asta, portafogli, prodotto e inventario.

Ogni movimento aggiorna `versione` e `data_modifica` nella stessa transazione.
Il componente verifica non negatività, riserva <= totale e limite NUMERIC(12,2).
Le nuove operazioni economiche devono riusarlo dopo aver acquisito i lock e
validato le regole del proprio dominio.

## Verifiche del 9 ottobre 2026

Su PostgreSQL 16 in un container dedicato senza volumi, con database di prova:

- Suite completa Producer/Consumer: **217 test superati**, zero fallimenti,
  errori o saltati, inclusi PostgreSQL, WebSocket e SMTP simulato.
- Dopo l'aggiunta dei casi di concorrenza rilancio/impostazione e portafoglio
  mancante, eseguiti tutti i **18 test del modulo portafoglio**, tutti superati.
- Verificati saldo iniziale zero, privacy del ledger, paginazione e ordinamento,
  incremento/riduzione/azzeramento del saldo, PUT identico, importi invalidi,
  accesso USER/ADMIN, identità nel servizio, revoca token, rollback, impostazioni
  concorrenti, riserva/rilascio e settlement senza duplicazione del ledger.
- Il test auth ora attende `200` dalla lettura del portafoglio autenticata,
  mantenendo le verifiche `401` dopo logout o disattivazione.

Comando della suite completa (creare prima un database dedicato e impostare
`DB_URL`, `DB_USERNAME` e `DB_PASSWORD` nell'ambiente):

```sh
RUN_DB_TESTS=true RUN_WS_TESTS=true ASTE_SCHEDULER_ENABLED=false \
./producer/mvnw -f pom.xml test \
  -DargLine=-javaagent:$HOME/.m2/repository/org/mockito/mockito-core/5.17.0/mockito-core-5.17.0.jar
```

La JVM locale era Java 25: l'agente esplicito evita il problema di aggancio
Mockito riscontrato in questo ambiente. Il progetto resta configurato per Java 21.
Le fixture del modulo portafoglio vengono rimosse dopo ogni test. Il container
dedicato è stato rimosso al termine; il database di sviluppo non è stato usato.
Il modulo non collega la chiusura automatica né implementa inventario o acquisti
fissi, che restano attività separate.
