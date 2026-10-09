# Offerte e chiusura — modulo TOMMI

## Perimetro e integrazione

Il modulo implementa rilanci, riserve e ledger, idempotenza, estensione di venti
secondi, settlement e ritiro delle offerte alla cancellazione dell'account.
Usa JDBC come il modulo auth e le tabelle Flyway esistenti.

Marco mantiene programmazione, modello/repository condivisi, blocco iniziale
dello stock e apertura delle aste. WebSocket/notifiche mantiene ticket,
controller STOMP, trasporto eventi ed email. Questo modulo non aggiunge
endpoint REST, controller STOMP, scheduler o pagine Consumer. Gli endpoint
di `aste.json` restano previsti. Dall'8 ottobre 2026 il modulo WebSocket richiama
`OffertaService` tramite `/app/aste/{id}/offerte` e trasporta gli eventi Spring
sui topic delle stanze. Il flusso completo richiede ancora il collegamento
della chiusura automatica e la configurazione SMTP. L'email è integrata
in `main` dal commit `c9e2a11`, come descritto sotto.

## Offerte

`OffertaService.piazza(long utenteId, long astaId, OffertaRequest request)` è
transazionale e richiede ROLE_USER, un `JwtAuthenticationToken` con subject
uguale a `utenteId`, e un USER attivo nel database. Il controller STOMP imposta
il contesto di sicurezza per ogni comando usando l'identità
verificata: non leggere `utenteId` dal body del browser.

Il DTO accetta `type: PLACE_BID`, UUID `clientBidId`, importo positivo con
massimo dieci cifre intere e due decimali, e `knownSequence` non negativa.
La prima offerta deve coprire il prezzo iniziale; le successive devono coprire
prezzo del leader più incremento minimo. Il leader non può rilanciare su sé
stesso. Il saldo disponibile deve coprire l'intero importo.

La scadenza è verificata dopo l'attesa dei lock: a `now == fine_at` il comando
è rifiutato. Ogni accettazione aggiunge venti secondi alla scadenza persistita.
Una sequenza arretrata non rifiuta un rilancio valido: la risposta segnala
`snapshotRequired`. Una sequenza futura produce `SEQUENCE_NON_AGGIORNATA`.

Il retry del medesimo UUID, utente, asta e importo restituisce `duplicata=true`
e lo stato corrente, anche dopo la scadenza; nessun nuovo movimento, timer o
evento. Dati diversi con lo stesso UUID producono `CLIENT_BID_ID_GIA_UTILIZZATO`.
Un'offerta ritirata non viene riattivata dal retry.

## Chiusura

`ChiusuraAstaService.chiudi(long astaId)` è un'operazione interna, richiamabile
dal job del ciclo di vita alla scadenza e durante il recupero dopo un riavvio.
Non va esposta come endpoint pubblico. Prima della scadenza e negli stati
diversi da APERTA non trasferisce nulla. Su CHIUSA restituisce il risultato
già persistito. La verifica dello stato avviene sotto lock dell'asta.

Il vincitore paga consumando la riserva; l'incasso va al portafoglio di
`aste.admin_id`, già obbligatorio nello schema. Una unità passa dallo stock
bloccato all'inventario; senza leader torna disponibile. `vincitore_id`,
`offerta_corrente` (prezzo finale) e `chiusa_at` permettono di costruire gli storici.
Un errore di stock, saldo o inventario annulla l'intera transazione.

## Persistenza e concorrenza

V10 modifica solo `offerte`: aggiunge `leader`, `ritirata_at` e indici operativi.
Un indice parziale impedisce due leader per asta; un'offerta ritirata non può
essere leader. I dati originari e gli UUID restano conservati. Il leader live
non viene salvato in `aste.vincitore_id`, che rimane il risultato finale.

Il comando blocca utente richiedente, asta, poi portafogli in ordine di ID.
Il lock utente è `FOR NO KEY UPDATE`, compatibile con i controlli delle chiavi
esterne durante l'assegnazione dell'inventario.
Il settlement blocca asta, portafogli nello stesso ordine, prodotto e inventario.
Il ritiro su più aste blocca prima tutte le aste in ordine di ID, poi tutti i
portafogli coinvolti in ordine di ID prima delle modifiche. Non acquisire il
lock di un altro utente dopo aver bloccato l'asta.

Ogni variazione di saldo registra un movimento con importo positivo e saldi
successivi. Gli aggiornamenti JDBC incrementano anche `versione` di asta,
prodotto, portafoglio e inventario. Gli altri moduli devono rispettare lock e
versioni; eventuali entity JPA già caricate vanno rilette dopo queste operazioni.

## Eliminazione account

Auth emette `EliminazioneUtenteRichiesta` in modo sincrono, nella transazione
di eliminazione e prima dell'anonimizzazione. `RitiroOfferteService` ritira tutte
le offerte dell'utente sulle aste non concluse né annullate. Gli storici delle
aste già concluse restano conservati.

Se l'utente è leader, la riserva viene liberata. Viene ripristinata la migliore
offerta precedente di un USER attivo con fondi sufficienti, riservandoli di nuovo.
Se nessuno la copre, leader e prezzo corrente diventano null: una nuova offerta
può partire dal prezzo iniziale. Le aste sono trattate in ordine di ID, usando
il saldo residuo dopo ciascun ripristino. Le estensioni già concesse rimangono;
il ripristino non aggiunge altri venti secondi.

Anonimizzazione, ritiro, riserve e ledger committano insieme; un errore annulla
anche l'eliminazione. Il lock dell'utente impedisce nuove sue offerte durante
la pulizia.

## Eventi ed errori

`EventoOfferte` viene pubblicato tramite Spring solo dopo il commit:
`BID_ACCEPTED` include UUID, importo, stato e `extensionSeconds=20`;
`AUCTION_CLOSED` include prodotto, vincitore e prezzo nello stato;
`AUCTION_SNAPSHOT` segnala il ritiro e l'eventuale ripristino del leader.
WebSocket/notifiche può usare `@EventListener` per adattare il payload STOMP
e richiedere l'email. Sono già eventi post-commit: non aggiungere un altro
`@TransactionalEventListener` AFTER_COMMIT. Scritture dai listener richiedono
una nuova transazione (`REQUIRES_NEW`). Username mascherati, nessun token o password.

Un rollback non pubblica eventi; un errore di listener viene registrato e non
annulla il settlement. Trasporto, retry email e consegna durevole restano al
modulo notifiche: questa pubblicazione Spring è best effort.

In `main` il listener email usa una nuova transazione per
accodare la richiesta; un job riconcilia le aste già CHIUSA con vincitore per
recuperare eventi persi. Invio e retry non richiamano il settlement. Vedere
[Notifiche email](10-notifiche-email.md); SMTP è disabilitato per default.

`OffertaException` segue il pattern del branch Prodotti: `ProblemDetail` con
`code`. Il trasporto STOMP deve adattarlo alla coda privata BID_REJECTED.
I codici aggiunti sono `RILANCIO_SU_SE_STESSO`, `CLIENT_BID_ID_GIA_UTILIZZATO`,
`SEQUENCE_NON_AGGIORNATA` per una sequenza futura, e `LIMITE_SALDO_SUPERATO`
per un incasso oltre NUMERIC(12,2), tutti con status 409. Le violazioni Jakarta
sono validate anche all'ingresso del servizio e vanno adattate dal trasporto.

## Test

JUnit 5/AssertJ, PostgreSQL reale e `RUN_DB_TESTS=true`, come i test auth.
Usare esclusivamente un database di prova. I fixture creano record univoci.

```powershell
$env:RUN_DB_TESTS = 'true'
$env:DB_URL = 'jdbc:postgresql://localhost:55433/liveauction_tests'
$env:DB_USERNAME = 'postgres'
$env:DB_PASSWORD = '<password del database di prova>'
.\producer\mvnw.cmd -f pom.xml verify
```

Le verifiche coprono validazioni, tempo server, concorrenza tra rilanci e chiusura,
retry, riserve su più aste, rollback, pubblicazione post-commit ed eliminazione
account con ripristino del leader.
