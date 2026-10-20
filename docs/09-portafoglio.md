# Portafoglio e ledger

Modulo integrato in main il 9 ottobre 2026; Matteo è subentrato sul modulo di Mondir.
Decisione aggiornata del 20 ottobre 2026: wallet di sola lettura e credito iniziale
configurabile. Il contratto corrente è in [REST](04-api-rest.md#portafoglio).

## Comportamento corrente

- GET /api/v1/me/portafoglio: saldi e movimenti personali paginati, ordinati per
  data e ID decrescenti. Lettura REPEATABLE_READ; ruolo USER e identità verificati
  anche nel servizio. Il Consumer conserva il token nella sessione server-side.
- La pagina /impostazioni/portafoglio mostra saldo totale, disponibile, riservato
  e ledger. Non contiene moduli di ricarica o impostazione. Il vecchio PUT
  /api/v1/me/portafoglio/impostazioni e il POST Consumer sono vietati con 403 per
  gli utenti autenticati. Rimossi controller, client e servizio di modifica.
- La registrazione crea nella stessa transazione utente USER, wallet e movimento
  CREDITO_INIZIALE, con riserva zero e asta_id null. Importo predefinito 10000.00
  in producer/src/main/resources/application.yaml: app.portafoglio.saldo-iniziale.
  Override opzionale WALLET_INITIAL_BALANCE, inoltrato anche da Compose.
- La configurazione ammette 0–9999999999.99, massimo due decimali; valori invalidi
  impediscono l'avvio. Con zero non si registra un movimento di importo zero.
  Il bonus è attribuito una sola volta: login, refresh e lettura non accreditano
  nulla. Modificare il valore e riavviare riguarda solo le nuove registrazioni.
- I saldi e i movimenti degli account esistenti restano invariati. I movimenti
  IMPOSTAZIONE_SALDO già presenti restano leggibili come dati storici.

## Operazioni economiche condivise

PortafoglioRepository centralizza lock ordinati per ID e movimenti di ledger.
Le operazioni di scrittura richiedono una transazione esistente (MANDATORY).
Ogni variazione aggiorna saldo, versione, data UTC e ledger atomicamente;
BigDecimal, riserva <= totale, non negatività e limite NUMERIC(12,2) invariati.
Rilanci, rilascio, pagamento, incasso e acquisti continuano a usare il componente.
La creazione fallisce se il wallet esiste già: non è un endpoint di ricarica.

V15 aggiunge CREDITO_INIZIALE al vincolo dei tipi di movimento, senza riscrivere
V8/V14 o modificare retroattivamente saldi. Nessun dato demo nelle migrazioni.
API, UI, test e collection Postman sono allineati alla nuova regola.

## Verifiche storiche (comportamento precedente)

La sezione seguente documenta il collaudo del 9 ottobre, quando il saldo iniziale
era zero e l'impostazione manuale era consentita. Non è il contratto attuale.

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
La chiusura automatica è ora collegata nello scheduler del modulo aste.
Il portafoglio non espone direttamente inventario o acquisti: dal 14 ottobre
sono implementati nei rispettivi domini. Gli acquisti riusano lock e ledger
condivisi: `ACQUISTO_FISSO` addebita il USER e `INCASSO_FISSO` accredita l'ADMIN
configurato con `SHOP_ADMIN_ID`, senza spendere il saldo riservato alle aste.
La manutenzione dei nuovi domini resta da assegnare nel team.
