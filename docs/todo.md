# Da fare — attività del team

Aggiornato al 9 ottobre 2026, sul `main` che include WebSocket e notifiche email
(base `c9e2a11`) e il modulo portafoglio completato da Matteo. Questo è il riferimento unico per le attività ancora da
completare. Le assegnazioni sono riassunte nel [README](../README.md#assegnazioni);
contratti e regole restano nei documenti di progettazione.

Quando un'attività viene integrata in `main`, aggiornare questa lista. Il codice
presente soltanto in un branch personale non va indicato come integrato.

## Priorità e dipendenze

1. **Matteo:** avviare il Consumer usando anche le API del portafoglio,
   integrate e verificate il 9 ottobre. Le operazioni economiche sono condivise
   con i servizi di Tommi.
2. **Marco e Tommi:** collegare la chiusura automatica al servizio esistente.
3. **Maikol:** configurare SMTP e verificare il flusso email dopo il collegamento
   della chiusura automatica; coda e retry sono già integrati.
4. **Matteo:** avviare il Consumer; autenticazione, catalogo e lobby possono
   partire subito. Il portafoglio può usare le nuove API integrate;
   inventario e storici richiedono ancora le API mancanti.
5. **Team:** assegnare le API inventario e gli acquisti a prezzo fisso.

## Matteo — portafoglio e movimenti (subentro a Mondir)

Dal 9 ottobre 2026 Matteo prende in carico il riallineamento e il completamento
del portafoglio, partendo dal lavoro di Mondir. È la priorità operativa di oggi;
Implementazione, verifiche e integrazione in `main` sono completate. Il Consumer resta in carico a Matteo.

Implementazione integrata in `main`. Il precedente branch `portafoglio-movimenti`
conteneva modelli incompatibili con Flyway e viene eliminato, essendo sostituito
dal modulo verificato. Il modulo usa proiezioni JDBC sulle
tabelle esistenti, senza introdurre quelle entity o modificare le migrazioni.

- Implementati `GET /api/v1/me/portafoglio` (saldi e ledger paginato) e
  `PUT /api/v1/me/portafoglio/impostazioni` secondo il contratto aggiornato.
- Estratti lock, aggiornamenti dei saldi e scrittura ledger da `OfferteRepository`
  a `PortafoglioRepository`; rilanci, ritiri e settlement delegano allo stesso
  componente. Conservati ordine dei lock, atomicità ed errori STOMP.
- Validati importi, ruolo USER, identità del token, riserve, PUT identico,
  rollback e concorrenza. Documentazione e Postman aggiornati.
- [x] Integrare le modifiche verificate in `main`, sostituendo i vecchi modelli
  incompatibili.

Dettagli e verifiche in [Portafoglio e ledger](09-portafoglio.md).

## Tommi — offerte e chiusura

Rilanci, estensioni, riserve/rilasci con ledger, ritiro offerte e settlement
sono già implementati. Il comando STOMP richiama `OffertaService`; il
settlement assegna già il prodotto all'inventario del vincitore.

- [ ] Collegare con Marco `ChiusuraAstaService` allo scheduler. Oggi un'asta
  scaduta rifiuta i rilanci, ma non viene chiusa e regolata automaticamente.
- [ ] Verificare chiusura con e senza vincitore, scadenza estesa, concorrenza
  tra rilancio e chiusura e recupero dopo un riavvio. Crediti, prodotto ed
  eventi non devono essere trasferiti o pubblicati due volte.

Le operazioni economiche sono ora estratte nel modulo portafoglio integrato;
`OfferteRepository` conserva le deleghe usate dai servizi. Per nuove operazioni
riusare `PortafoglioRepository`, mantenendo ordine dei lock, ledger e atomicità.

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

- [x] Implementare l'email riepilogativa al vincitore dopo il commit della
  chiusura, coordinandosi con Marco e Tommi.
- [x] Gestire errori e retry dell'invio: un errore email non deve annullare
  la vittoria o ripetere il settlement.

Integrati in `main` nel commit `c9e2a11`: listener post-commit, coda V11,
invio su executor dedicato, recupero e retry persistenti. SMTP resta disabilitato
per default. Dettagli in [Notifiche email](10-notifiche-email.md).

- [ ] Configurare e attivare SMTP nell'ambiente concordato dal team.
- [ ] Verificare il flusso end-to-end dopo il collegamento della chiusura
  automatica da parte di Marco/Tommi.

## Cristian — prodotti e catalogo

L'area assegnata è completa: categorie, prodotti, CRUD ADMIN, filtri,
paginazione e gestione dello stock.

- [x] Verificare che documentazione e collection Postman descrivano gli
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
