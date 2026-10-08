# Da fare — attività del team

Aggiornato all'8 ottobre 2026, sul `main` che include l'integrazione WebSocket
(commit `1178810`). Questo è il riferimento unico per le attività ancora da
completare. Le assegnazioni sono riassunte nel [README](../README.md#assegnazioni);
contratti e regole restano nei documenti di progettazione.

Quando un'attività viene integrata in `main`, aggiornare questa lista. Il codice
presente soltanto in un branch personale non va indicato come integrato.

## Priorità e dipendenze

1. **Mondir e Tommi:** riallineare il portafoglio e concordare le operazioni
   economiche già esistenti, evitando implementazioni duplicate.
2. **Marco e Tommi:** collegare la chiusura automatica al servizio esistente.
3. **Maikol:** completare l'email al vincitore, da attivare dopo la chiusura.
4. **Matteo:** avviare il Consumer; autenticazione, catalogo e lobby possono
   partire subito. Portafoglio, inventario e storici richiedono le API mancanti.
5. **Team:** assegnare le API inventario e gli acquisti a prezzo fisso.

## Mondir — portafoglio e movimenti

Il branch `portafoglio-movimenti` non è integrato: le entità non corrispondono
allo schema Flyway e `ddl-auto=validate` impedisce l'avvio del Producer.

- [ ] Aggiornare il branch con `main` e adattare `Wallet`, `Movimento` e
  `TipoMovimento` allo [schema database](03-database.md).
- [ ] Usare `portafogli` e `movimenti_portafoglio`, con `portafoglio_id` e i
  campi previsti (`data_movimento`, `asta_id`, `saldo_totale_dopo`,
  `saldo_riservato_dopo`). Riallineare anche i tipi di movimento agli enum
  definiti nello schema. Non modificare le migrazioni V2/V8 già applicate.
- [ ] Implementare `GET /api/v1/me/portafoglio`, con saldo totale, riservato,
  disponibile e movimenti, secondo il [contratto REST](04-api-rest.md).
- [ ] Concordare con Tommi come riusare o estrarre riserva, rilascio, pagamento
  e incasso già implementati via JDBC. Ogni modifica al saldo deve avere un
  movimento di ledger; gli importi usano `BigDecimal`.
- [ ] Verificare avvio e test con PostgreSQL prima di proporre il merge.

## Tommi — offerte e chiusura

Rilanci, estensioni, riserve/rilasci con ledger, ritiro offerte e settlement
sono già implementati. Il comando STOMP richiama `OffertaService`; il
settlement assegna già il prodotto all'inventario del vincitore.

- [ ] Collegare con Marco `ChiusuraAstaService` allo scheduler. Oggi un'asta
  scaduta rifiuta i rilanci, ma non viene chiusa e regolata automaticamente.
- [ ] Verificare chiusura con e senza vincitore, scadenza estesa, concorrenza
  tra rilancio e chiusura e recupero dopo un riavvio. Crediti, prodotto ed
  eventi non devono essere trasferiti o pubblicati due volte.
- [ ] Concordare con Mondir le interfacce delle operazioni economiche, mantenendo
  ordine dei lock, ledger e atomicità. Non duplicare le operazioni esistenti.

La chiusura resta un servizio interno: non serve un endpoint pubblico di
settlement. Il relay degli eventi WebSocket è già collegato.

## Marco — programmazione aste

Creazione ADMIN, prenotazione stock, apertura automatica, lobby e snapshot
sono già implementati.

- [ ] Implementare l'annullamento delle aste secondo il contratto, con gestione
  coerente dello stock e degli eventi.
- [ ] Implementare `GET /api/v1/me/vittorie` e
  `GET /api/v1/admin/aste/storico`, rispettando visibilità USER/ADMIN.
- [ ] Collegare con Tommi lo scheduler alla chiusura delle aste APERTA scadute,
  sia nei cicli periodici sia al riavvio. Isolare gli errori per asta e
  ritentare al ciclo successivo.
- [ ] Verificare la stabilità di `dueJobConcorrentiNonDuplicanoStatiOEventi`:
  il piano precedente segnalava un fallimento intermittente. L'ultima
  integrazione ha corretto il conteggio degli eventi nei test del ciclo di vita;
  verificare se resta un problema prima di introdurre altre correzioni.
- [ ] Aggiornare contratto e Postman per annullamento e storici.

## Maikol — WebSocket e notifiche

Ticket, STOMP, presenza, comando rilancio, conferme/rifiuti privati e relay
degli eventi post-commit sono integrati in `main`. Anche la pulizia dei dati
dei test WebSocket è stata completata.

- [ ] Implementare l'email riepilogativa al vincitore dopo il commit della
  chiusura, coordinandosi con Marco e Tommi.
- [ ] Gestire errori e retry dell'invio: un errore email non deve annullare
  la vittoria o ripetere il settlement.

Nota di avanzamento Maikol (branch `web_socket`, non ancora mergiato): implementati
listener post-commit, coda V11, invio su executor dedicato, recupero e retry
persistenti. SMTP resta disabilitato per default. Le caselle sopra restano aperte
finché l'attività non viene integrata in `main`. Dettagli in
[Notifiche email](10-notifiche-email.md). Restano configurazione SMTP e verifica
end-to-end dopo il collegamento della chiusura automatica da parte di Marco/Tommi.

## Cristian — prodotti e catalogo

L'area assegnata è completa: categorie, prodotti, CRUD ADMIN, filtri,
paginazione e gestione dello stock.

- [ ] Verificare che documentazione e collection Postman descrivano gli
  endpoint realmente disponibili dopo le integrazioni.

Gli acquisti a prezzo fisso restano da assegnare nel team.

## Matteo — autenticazione e Consumer/frontend

L'autenticazione Producer è completa. Matteo prende in carico il
**Consumer/frontend**, oggi ancora uno scheletro senza pagine applicative.

- [ ] Implementare client REST e flusso web di registrazione, login, refresh e
  logout. Conservare i token nella sessione server-side del Consumer.
- [ ] Realizzare marketplace/catalogo, lobby e stanza live con Thymeleaf e
  JavaScript: ticket STOMP, presenza, rilanci, conferme/rifiuti, countdown
  sincronizzato, feed e annuncio del vincitore.
- [ ] Gestire riconnessione e recupero tramite snapshot REST quando manca
  un evento o si rileva un salto di `sequence`.
- [ ] Realizzare portafoglio, movimenti, inventario e vittorie personali quando
  le rispettive API saranno disponibili.
- [ ] Realizzare il pannello ADMIN per prodotti/categorie, programmazione,
  annullamento e storico delle aste.
- [ ] Gestire errori di validazione, sessione scaduta e Producer non disponibile.
- [ ] Supportare la protezione delle nuove rotte Producer: ruoli nel contratto
  e `@PreAuthorize` sulle operazioni di dominio. Le letture riservate sotto
  `/prodotti/**` o `/aste/**` richiedono regole HTTP più specifiche prima dei
  GET pubblici.

Il Consumer usa le API del Producer e non accede direttamente al database.
Riferimento per le pagine: [UI e flussi](05-ui-flussi.md).

## Da assegnare nel team

- [ ] **API inventario:** implementare `GET /api/v1/me/inventario` e il relativo
  modulo. L'assegnazione al vincitore è già presente nel settlement di Tommi.
- [ ] **Acquisti a prezzo fisso:** implementare
  `POST /api/v1/prodotti/{id}/acquisti`, coordinando stock, portafoglio,
  ledger e inventario in una transazione.

Queste responsabilità richiedono una decisione del team; la UI relativa è di Matteo.

## Per completare ogni attività

- Aggiornare il proprio branch con `main` prima di iniziare.
- Rispettare [requisiti](01-requisiti.md), [schema](03-database.md) e
  [contratto REST/WebSocket](04-api-rest.md); aggiornare contratto e Postman
  quando cambiano gli endpoint.
- Aggiungere migrazioni versionate quando servono, senza riscrivere quelle
  applicate. Non inserire dati demo nelle migrazioni.
- Eseguire i test Producer con PostgreSQL di prova (`RUN_DB_TESTS=true` e
  `DB_URL`); abilitare anche `RUN_WS_TESTS=true` per i test WebSocket.
  I test devono rimuovere i propri dati e non interferire con altre suite.
- Proporre il merge con verifiche superate e aggiornare questo documento
  quando l'attività è integrata.
