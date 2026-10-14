# Attività del team

## Aggiornamento locale — 14 ottobre 2026

Sulla base `ede0201`, senza commit: aggiunti `GET /me/inventario`, pagina
Consumer `/inventario`, navigazione, DTO e paginazione. Sono visibili anche
i prodotti posseduti non più pubblici; i risultati sono isolati per utente.
Corretto il feed per scartare eventi vecchi/duplicati prima della cronologia,
con regressione JavaScript. Contratto e Postman inventario aggiornati.

Il richiedente ha confermato accredito a un ADMIN configurato, quantità intera
1–100 e UUID idempotente. Implementati API e UI acquisto, migrazioni V13/V14,
stock/crediti/ledger/inventario atomici e retry della UI con lo stesso UUID.
Impostare `SHOP_ADMIN_ID` con l'ID di un ADMIN attivo; default zero disabilita
gli acquisti senza effetti economici. Nessuna nuova assegnazione personale.

`clean verify` con Java 21/PostgreSQL: 293 Producer e 52 Consumer superati,
zero errori/fallimenti/saltati; 12 test JavaScript superati. Provato anche
acquisto reale dalla UI: 2 unità a 12,50, saldo 100→75, inventario 2 e incasso
ADMIN 25. Database temporaneo isolato. Dettagli in
[inventario e acquisti](13-inventario-acquisti.md).
Avvio Docker `prod` verificato su stack isolato: tre servizi healthy e
login/acquisto/inventario/portafoglio dalla UI riusciti. Provato anche un flusso
asta con un browser e un secondo client STOMP, fino al vincitore persistito;
non sostituisce tutta la matrice manuale a due browser.

Le sezioni sotto conservano il piano del 13 ottobre, aggiornato per le nuove
funzioni; le verifiche manuali non eseguite restano senza spunta.

Stato di `main` al **13 ottobre 2026**, dopo le consegne di Marco
(`4138bd5`) e Andrea (`c864b2f`) e la revisione delle loro modifiche.
Annullamento, vittorie, storico e UI Consumer sono integrati: non sono più
soltanto modifiche locali o lavoro su branch separati.

Una spunta indica una funzionalità implementata. Le verifiche complete dal
browser restano attività distinte anche quando esistono test automatici.
Ogni responsabile cura codice, test, documentazione e Postman della propria
area. Le verifiche residue sono ripartite secondo le assegnazioni del README;
la manutenzione di inventario e acquisti fissi resta **da assegnare dal team**.
L'attivazione SMTP esterna è fuori scope per la consegna.

## Priorità e dipendenze

1. Configurare l'ADMIN destinatario degli acquisti nell'ambiente locale.
2. Il team assegna la manutenzione di inventario/acquisti, già implementati e documentati.
3. Nel frattempo ogni responsabile collabora al collaudo dei flussi già disponibili.
4. Il team verifica il sistema completo e lo stack Docker nell'ambiente di consegna.
5. Si aggiornano stato, risultati delle verifiche e istruzioni della demo sul commit finale.

## Funzionalità implementate

| Chi | Che cosa | Fatto |
| :--- | :--- | :---: |
| Matteo | **Autenticazione:** registrazione USER e creazione del portafoglio iniziale nella stessa transazione. | ✓ |
| Matteo | **Autenticazione:** login, refresh, logout, token, ruoli e revoca delle sessioni. | ✓ |
| Matteo | **Account:** eliminazione e anonimizzazione, con ritiro delle offerte e conservazione degli storici. | ✓ |
| Matteo | **Portafoglio:** API del saldo e dei movimenti personali paginati. | ✓ |
| Matteo | **Portafoglio:** impostazione del saldo virtuale, validazioni e registrazione del movimento. | ✓ |
| Matteo | **Operazioni economiche:** repository condiviso per saldo, riserve e ledger, integrato con offerte e chiusura. | ✓ |
| Cristian | **Catalogo pubblico:** categorie e prodotti, ricerca, filtri, ordinamento e paginazione. | ✓ |
| Cristian | **Gestione ADMIN:** creazione, modifica e lettura di categorie e prodotti, compresi quelli inattivi. | ✓ |
| Cristian | **Visibilità:** prodotti e categorie attivi, disattivazione e riattivazione senza alterare lo stock. | ✓ |
| Cristian | **Stock:** disponibilità, flag astabile e controllo della versione; quantità bloccata gestita dal dominio aste. | ✓ |
| Cristian | **Contratto e Postman:** allineamento degli endpoint del catalogo, DTO, body, variabili e controlli delle risposte. | ✓ |
| Tommi | **Offerte:** validazione, incremento minimo, tempo server ed estensione di 20 secondi. | ✓ |
| Tommi | **Crediti:** riserva e rilascio atomico dei fondi, con ledger e lock ordinati. | ✓ |
| Tommi | **Idempotenza:** retry dello stesso clientBidId senza nuove offerte, riserve o estensioni. | ✓ |
| Tommi | **Ritiro offerte:** cancellazione account e ripristino del precedente leader con fondi sufficienti. | ✓ |
| Tommi | **Chiusura:** pagamento, incasso ADMIN e assegnazione del prodotto al vincitore una sola volta; rilascio dello stock senza vincitore. | ✓ |
| Tommi | **Scheduler:** integrazione della chiusura periodica e al riavvio, con retry e test di concorrenza/rollback. | ✓ |
| Tommi | **Snapshot:** lettura del leader operativo dopo il ritiro delle offerte. | ✓ |
| Marco | **Programmazione:** creazione ADMIN con input Europe/Rome, conversione UTC e prenotazione atomica dello stock. | ✓ |
| Marco | **Ciclo di vita:** apertura della stanza e dell'asta, recupero al riavvio e collegamento della chiusura automatica. | ✓ |
| Marco | **Letture:** lobby filtrabile/paginata e snapshot pubblico con timer e sequence. | ✓ |
| Marco | **Annullamento — contratto:** definire stati ammessi, comportamento con offerte, ripetizione del comando, DTO ed errori di `POST /admin/aste/{id}/annullamento`. | ✓ |
| Marco | **Annullamento — servizio:** implementare transazione e lock, rilascio dello stock ed eventuali fondi secondo le regole concordate. | ✓ |
| Marco | **Annullamento — API:** aggiungere controller e validazione, con autorizzazione ADMIN anche sul servizio. | ✓ |
| Marco | **Annullamento — evento:** incrementare sequence e pubblicare AUCTION_CANCELLED dopo il commit, usando il payload delle transizioni già gestito dal relay di Maikol. | ✓ |
| Marco | **Annullamento — test:** verificare stati non ammessi, accessi, ripetizione, rollback e concorrenza con offerte/chiusura, senza doppi rilasci. | ✓ |
| Marco | **Vittorie personali — contratto:** definire DTO, paginazione e ordinamento di `GET /me/vittorie`, con prodotto, prezzo finale e data. | ✓ |
| Marco | **Vittorie personali — API:** implementare lettura e controller USER, limitando i risultati all'utente del token. | ✓ |
| Marco | **Vittorie personali — test:** verificare isolamento tra utenti, lista vuota, paginazione ed esclusione delle aste senza vincitore. | ✓ |
| Marco | **Storico ADMIN — contratto:** definire DTO, stati inclusi, paginazione, ordinamento e filtri per prodotto, vincitore e intervallo temporale. | ✓ |
| Marco | **Storico ADMIN — API:** implementare `GET /admin/aste/storico`, con autorizzazione ADMIN anche nel servizio. | ✓ |
| Marco | **Storico ADMIN — test:** verificare filtri combinati, estremi delle date, aste senza vincitore, paginazione e accessi non autorizzati. | ✓ |
| Marco | **Documentazione e Postman:** aggiornare contratto, esempi e collection per annullamento e storici quando le API sono implementate. | ✓ |
| Maikol | **Accesso live:** ticket monouso, handshake STOMP e autorizzazione per comando. | ✓ |
| Maikol | **Presenza:** join, disconnessioni, utenti distinti tra più schede e nomi mascherati. | ✓ |
| Maikol | **Rilanci:** comando STOMP delegato al servizio di Tommi, conferme/rifiuti alla sessione mittente. | ✓ |
| Maikol | **Eventi:** relay di apertura, offerte, snapshot e chiusura dopo il commit. | ✓ |
| Maikol | **Notifiche:** email riepilogativa, coda persistente, recupero e retry, con verifiche locali. | ✓ |
| Andrea | **Consumer:** scheletro Spring Boot e configurazione di base su porta 8082. | ✓ |
| Andrea | **Client REST:** configurare comunicazione con il Producer, DTO, Bearer token e gestione di Problem Details, timeout e Producer non disponibile. | ✓ |
| Andrea | **Registrazione e login:** realizzare pagine e flussi web, conservando access/refresh token nella sessione server-side. | ✓ |
| Andrea | **Sessione e sicurezza:** implementare refresh, logout, scadenza e protezione delle pagine USER/ADMIN; non esporre i token al JavaScript. | ✓ |
| Andrea | **Marketplace:** realizzare catalogo e dettaglio con filtri, paginazione, indicazione astabile e quantità disponibile/bloccata. | ✓ |
| Andrea | **Portafoglio:** realizzare saldi, impostazione del totale e movimenti paginati; mostrare errori e saldo riservato. Le API sono già disponibili. | ✓ |
| Andrea | **Lobby:** realizzare filtri di stato/categoria/testo e countdown basati sul tempo del server. Le API sono già disponibili. | ✓ |
| Andrea | **Catalogo ADMIN:** realizzare gestione categorie/prodotti e PUT completo con versione; gestire il 409 rileggendo il prodotto. | ✓ |
| Andrea | **Programmazione ADMIN:** realizzare selezione del prodotto disponibile/astabile, input Europe/Rome, prezzo e riepilogo dello stock prenotato. | ✓ |
| Andrea | **Stanza — ingresso:** recuperare snapshot e ticket, sottoscrivere topic/coda privata e inviare join; mostrare pre-live e presenza. | ✓ |
| Andrea | **Stanza — rilanci:** realizzare form, clientBidId e knownSequence; gestire BID_CONFIRMED/BID_REJECTED e retry senza duplicare il comando. | ✓ |
| Andrea | **Stanza — aggiornamenti:** sincronizzare countdown, prezzo, leader, feed e scadenza; mostrare vincitore/importo o chiusura senza offerte. | ✓ |
| Andrea | **Stanza — riconnessione:** richiedere nuovo ticket e snapshot, recuperare gap di sequence e ignorare eventi vecchi/duplicati; disabilitare il form durante il riallineamento. | ✓ |
| Andrea | **Vittorie e storico:** realizzare pagine USER e ADMIN dopo le API di Marco, con filtri/paginazione concordati. | ✓ |
| Andrea | **Annullamento:** aggiungere il comando ADMIN e gestire l'esito nella stanza dopo l'integrazione di API ed evento. | ✓ |
| Andrea | **Documentazione Consumer:** aggiornare rotte, flussi e istruzioni di avvio/demo delle pagine effettivamente integrate. | ✓ |
| Maikol | **Annullamento live:** il relay inoltra AUCTION_CANCELLED; payload documentato e test WebSocket di commit, rollback e retry presenti. | ✓ |
| Andrea | **Saldi nella stanza:** rilettura del portafoglio dopo BID_ACCEPTED, anche quando un altro utente libera la riserva del precedente leader; test di regressione. | ✓ |

## Attività residue

Le righe senza spunta indicano verifiche ancora da completare; le nuove API
e pagine sono implementate nella copia di lavoro del 14 ottobre.

### Matteo — sicurezza, account e portafoglio

| Chi | Che cosa | Fatto |
| :--- | :--- | :---: |
| Matteo | **Flusso auth reale:** registrazione dalla UI e portafoglio a zero; login, refresh, logout, revoca e sessione scaduta con Producer reale. | &nbsp; |
| Matteo | **Refresh concorrente:** verificare richieste simultanee e più schede durante la rotazione dei token, anche dalla stanza. | &nbsp; |
| Matteo | **Autorizzazioni:** verificare anonimi, ruolo errato e isolamento dei dati sulle nuove rotte; Bearer/refresh token solo nella sessione server-side, CSRF sulle scritture Consumer. | &nbsp; |
| Matteo | **Account:** provare eliminazione dal browser, revoca delle sessioni, ritiro offerte, aggiornamento del leader e conservazione degli storici con Tommi. | &nbsp; |
| Matteo | **Portafoglio:** verificare saldi e ledger dal browser, impostazione del totale con fondi riservati e messaggio di conflitto. | &nbsp; |
| Matteo | **Nuove API:** supportare i referenti di inventario/acquisti per identità del token, ruoli e operazioni condivise su saldo/ledger; nessuna nuova assegnazione implicita. | &nbsp; |

### Cristian — catalogo e stock

| Chi | Che cosa | Fatto |
| :--- | :--- | :---: |
| Cristian | **Pannello reale:** provare creazione/modifica categorie e prodotti, disattivazione/riattivazione e visibilità nel catalogo pubblico con Andrea. | &nbsp; |
| Cristian | **Catalogo:** verificare ricerca, filtri, paginazione, dettaglio e liste vuote con Producer reale. | &nbsp; |
| Cristian | **Versioni:** provare modifiche concorrenti e 409; la UI rilegge il prodotto, mostra i valori tentati/correnti e richiede un nuovo invio. | &nbsp; |
| Cristian | **Stock:** verificare disponibile/bloccato dopo programmazione, annullamento e chiusura; quantità bloccata non modificabile dal form. | &nbsp; |
| Cristian | **Acquisti fissi:** coordinare acquistabilità, stock e versioni con il referente ancora da decidere; aggiornare contratto/Postman se cambiano le API del catalogo. | &nbsp; |

### Tommi — offerte e settlement

| Chi | Che cosa | Fatto |
| :--- | :--- | :---: |
| Tommi | **Rilanci reali:** due utenti, incremento minimo, prima offerta, saldo insufficiente, offerte simultanee e limite di scadenza. | &nbsp; |
| Tommi | **Riserve:** verificare riserva del leader e rilascio del precedente, ledger e saldi mostrati nelle diverse schede con Andrea. | &nbsp; |
| Tommi | **Idempotenza:** reinviare lo stesso clientBidId senza nuove offerte, riserve o estensioni; verificare risposta privata e UI. | &nbsp; |
| Tommi | **Chiusura:** controllare addebito, incasso ADMIN, ledger e prodotto assegnato una sola volta; senza offerte verificare rilascio dello stock. | &nbsp; |
| Tommi | **Ritiro:** provare eliminazione account del leader e ripristino del precedente offerente con fondi sufficienti, insieme a Matteo. | &nbsp; |
| Tommi | **Concorrenza e ripartenza:** verificare offerta/chiusura e retry dello scheduler con Marco; controllare la compatibilità economica degli acquisti fissi col futuro referente. | &nbsp; |

### Marco — programmazione, annullamento e storici

| Chi | Che cosa | Fatto |
| :--- | :--- | :---: |
| Marco | **Programmazione dalla UI:** input Europe/Rome, conversione UTC, validazioni, prenotazione stock e comparsa nella lobby. | &nbsp; |
| Marco | **Transizioni reali:** stanza a -3 minuti, apertura e durata iniziale di 7 minuti; countdown coerenti con il tempo del Producer. | &nbsp; |
| Marco | **Annullamento dalla UI:** consentito senza offerte; rifiutato con offerte anche ritirate, asta chiusa/scaduta; retry senza doppi rilasci. | &nbsp; |
| Marco | **Concorrenza:** provare annullamento/offerta/chiusura con Tommi e verificare stock, stato e sequence. | &nbsp; |
| Marco | **Storici dalle pagine:** vittorie del solo utente; storico ADMIN, filtri combinati, estremi delle date, paginazione, aste annullate e chiuse senza vincitore. | &nbsp; |
| Marco | **Riavvio:** provare recupero delle transizioni scadute, chiusura idempotente e retry dopo errore, con Tommi. | &nbsp; |
| Marco | **Documentazione di dominio:** riallineare i documenti correnti che descrivono ancora il lavoro come locale; preservare i report storici come tali. | &nbsp; |

### Maikol — trasporto live e notifiche

| Chi | Che cosa | Fatto |
| :--- | :--- | :---: |
| Maikol | **Annullamento integrato:** verificare dal comando ADMIN alla stanza browser AUCTION_CANCELLED, sequence e disabilitazione rilanci. Relay e test sono già implementati. | &nbsp; |
| Maikol | **Accesso:** provare ticket monouso/scaduto, handshake, sottoscrizioni e comandi non autorizzati con il Consumer reale. | &nbsp; |
| Maikol | **Presenza:** provare due utenti e più schede dello stesso utente; disconnessione di una scheda e uscita definitiva. | &nbsp; |
| Maikol | **Eventi:** conferme/rifiuti privati, aggiornamenti pubblici e ordine delle sequence; verificare con Andrea duplicati, gap e riconnessione con nuovo ticket/snapshot. | &nbsp; |
| Maikol | **Ambiente live:** controllare URL raggiungibile dal browser e origine consentita; coordinare con Andrea eventuali host/porte diversi e HTTPS/wss. | &nbsp; |
| Maikol | **Email locale:** provare chiusura con vincitore, coda post-commit, errore/retry e recupero; nessuna email per annullamento o asta senza vincitore. Provider SMTP esterno escluso. | &nbsp; |

### Andrea — Consumer e verifiche web

| Chi | Che cosa | Fatto |
| :--- | :--- | :---: |
| Andrea | **Inventario:** client e DTO, pagina /inventario, navigazione, quantità, paginazione, lista vuota e gestione errori aggiunti nella copia di lavoro del 14 ottobre. | ✓ |
| Andrea | **Acquisto:** modulo nel dettaglio, quantità 1–100, conferma, errori, inventario aggiornato e retry con stesso UUID dopo esito incerto; test Consumer aggiunti. | ✓ |
| Andrea | **Flussi reali:** provare login, registrazione, account, portafoglio, catalogo e pannello ADMIN con Producer/PostgreSQL reali; i test MVC attuali simulano il Producer. | &nbsp; |
| Andrea | **Sessione/errori:** provare refresh, scadenza, logout, CSRF, ruolo errato, timeout e Producer indisponibile; controllare messaggi e pagine 400/403/404/500. | &nbsp; |
| Andrea | **Stanza reale:** pre-live, join, due utenti/più schede, rilanci, saldi, chiusura e annullamento; perdita rete, nuovo ticket e snapshot prima di riabilitare il form. | &nbsp; |
| Andrea | **Interfaccia:** verificare filtri mantenuti, paginazione, liste vuote, validazioni e conflitti di versione, leggibilità su schermi diversi. | &nbsp; |
| Andrea | **Consegna:** verificare sessioni/cookie e URL WebSocket nell’ambiente scelto, coordinandosi con Matteo e Maikol; aggiornare test e flussi dopo le pagine mancanti/fix. | &nbsp; |

### Inventario — implementato, manutenzione da assegnare

| Chi | Che cosa | Fatto |
| :--- | :--- | :---: |
| Team | **Referente e contratto:** assegnare l’API; concordare campi prodotto/quantità, paginazione, ordinamento e visibilità dei prodotti posseduti divenuti inattivi con Andrea. | &nbsp; |
| Da assegnare | **Lettura:** GET /api/v1/me/inventario implementato nella copia locale, senza duplicare l'assegnazione dei prodotti. | ✓ |
| Da assegnare | **Sicurezza e DTO:** identità dal token, accesso USER e isolamento degli inventari; DTO senza dati privati di altri utenti. | ✓ |
| Da assegnare | **Test:** lista vuota, parametri invalidi, paginazione/ordinamento stabile, anonimo/ruolo errato, isolamento e quantità dopo più vittorie dello stesso prodotto. | ✓ |
| Da assegnare | **Contratto/Postman:** esempi, errori e collection aggiornati; runner e accettazione finale restano verifiche distinte. | ✓ |

### Acquisti fissi — implementati, manutenzione da assegnare

| Chi | Che cosa | Fatto |
| :--- | :--- | :---: |
| Team | **Regole:** confermati ADMIN configurato, quantità 1–100 e UUID idempotente. Resta da assegnare il referente di manutenzione. | ✓ |
| Da assegnare | **Contratto:** POST /api/v1/prodotti/{id}/acquisti, DTO, validazioni, risposta, status e codici di errore documentati. | ✓ |
| Da assegnare | **Transazione:** stock disponibile, saldo, ledger e inventario atomici; BigDecimal, repository condiviso, lock e versioni compatibili con aste. Stock bloccato escluso. | ✓ |
| Da assegnare | **API protetta:** controller e servizio USER, identità dal token, nessun effetto parziale e retry idempotente. | ✓ |
| Da assegnare | **Test funzionali:** quantità invalide, prezzo assente/prodotto non acquistabile, stock esaurito, saldo insufficiente, destinatario crediti, ledger e inventario. | ✓ |
| Da assegnare | **Test transazionali:** ultima unità concorrente, acquisto/programmazione asta, rollback e retry senza doppi addebiti o assegnazioni. | ✓ |
| Da assegnare | **Documentazione/Postman:** contratto e collection aggiornati; acquisto e aggiornamento dati provati nella UI. Runner Postman non eseguito. | ✓ |

### Team — accettazione e consegna

| Chi | Che cosa | Fatto |
| :--- | :--- | :---: |
| Team | **Suite finale:** clean verify Java 21/PostgreSQL con RUN_DB_TESTS/RUN_WS_TESTS: 293 Producer + 52 Consumer, 12 JavaScript. Base ede0201, modifiche senza commit. | ✓ |
| Team | **Demo aste:** ADMIN crea prodotto e programma; due USER partecipano/rilanciano; chiusura con saldo, ledger, prodotto persistito e storici coerenti. | &nbsp; |
| Team | **Scenari alternativi:** senza offerte, annullamento, saldo insufficiente, ritiro leader, riconnessione e riavvio; ogni responsabile risolve i problemi del proprio dominio. | &nbsp; |
| Team | **Nuove funzioni:** inventario vuoto, acquisto fisso di 2 unità, inventario aggiornato e saldo/ledger provati con Producer/PostgreSQL reali. | ✓ |
| Team | **Postman:** eseguire le collection sugli endpoint implementati e verificare variabili, body, errori e controlli delle risposte. | &nbsp; |
| Team | **Avvio/Docker:** JAR indipendenti su localhost e compose prod isolato verificati, tre servizi healthy; login e acquisto dalla UI riusciti, dati esistenti preservati. | ✓ |
| Team | **Configurazione:** Java 21, JWT da ambiente, sessioni coerenti con HTTP/HTTPS (cookie Secure nel profilo prod), URL WebSocket accessibile dal browser e origine autorizzata. | &nbsp; |
| Team | **Procedura demo:** documentare creazione account/ADMIN, crediti, categorie/prodotti e aste ripetibili; nessun dato demo nelle migrazioni. | &nbsp; |
| Team | **Allineamento:** README, TODO, documenti di stato e istruzioni devono descrivere il main finale; distinguere verifiche correnti e report storici. | &nbsp; |

## Proposte e limiti da decidere separatamente

Questi punti non diventano automaticamente obblighi di consegna. Il team decide
se implementarli oppure conservarli come limiti documentati.

| Punto | Stato | Coinvolgimento se approvato |
| :--- | :--- | :--- |
| Lobby “In partenza” / “a cui partecipo” | Comportamento e filtri REST dedicati da concordare. | Marco e Andrea; altri domini secondo il filtro. |
| Vincitore/importo nella card lobby | Disponibili nella stanza e negli storici, assenti nella card. | Marco e Andrea. |
| Feed ricostruibile dopo reload | Eventi mostrati durante la sessione della pagina; manca GET dedicato. | Contratto da concordare fra Tommi, Marco, Maikol e Andrea. |

## Verifica della revisione

La revisione di `3dfeec2..c864b2f` ha controllato contratti, ruoli, isolamento,
transazioni, stock, relay post-commit, sessione Consumer e stanza STOMP.
Corretto il mancato aggiornamento dei saldi dopo il rilancio di un altro utente,
con una prova di regressione JavaScript.

Gli esiti della verifica corrente sono nel README. Le prove automatiche non
sostituiscono il collaudo completo del browser o l'avvio dello stack Docker
`prod`, che restano nelle attività residue. I report del 7–12 ottobre conservano
le verifiche storiche precedenti al Consumer attuale.
