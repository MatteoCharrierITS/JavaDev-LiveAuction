# Attività del team

Stato della base `main` al 12 ottobre 2026 e delle attività di Marco
completate nella copia locale (non ancora pubblicate). La colonna **Fatto**
contiene una spunta per le attività completate; una cella vuota indica lavoro
ancora da terminare. Ogni responsabile cura anche test, documentazione e
collection Postman della propria porzione. Inventario e acquisti fissi restano
da assegnare nel team; il Consumer è di Andrea. L'attivazione di un server SMTP
esterno è fuori scope e non è prevista per la consegna.

## TODO

| Chi | Che cosa | Fatto |
| :--- | :--- | :---: |
| Matteo | **Autenticazione:** registrazione USER e creazione del portafoglio iniziale nella stessa transazione. | ✓ |
| Matteo | **Autenticazione:** login, refresh, logout, token, ruoli e revoca delle sessioni. | ✓ |
| Matteo | **Account:** eliminazione e anonimizzazione, con ritiro delle offerte e conservazione degli storici. | ✓ |
| Matteo | **Portafoglio:** API del saldo e dei movimenti personali paginati. | ✓ |
| Matteo | **Portafoglio:** impostazione del saldo virtuale, validazioni e registrazione del movimento. | ✓ |
| Matteo | **Operazioni economiche:** repository condiviso per saldo, riserve e ledger, integrato con offerte e chiusura. | ✓ |
| Matteo | **Sicurezza delle nuove API:** supportare i responsabili nella verifica di ruoli, identità del token e regole HTTP; controllare accessi anonimi, ruolo errato e dati di altri utenti. | &nbsp; |
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
| Maikol | **Annullamento live:** collegare AUCTION_CANCELLED al topic della stanza quando Marco consegna evento e payload; verificare sequence e assenza di pubblicazione in caso di rollback. | &nbsp; |
| Maikol | **Contratto WebSocket:** aggiornare payload ed esempi dell'evento di annullamento quando viene integrato. | &nbsp; |
| Andrea | **Consumer:** scheletro Spring Boot e configurazione di base su porta 8082. | ✓ |
| Andrea | **Client REST:** configurare comunicazione con il Producer, DTO, Bearer token e gestione di Problem Details, timeout e Producer non disponibile. | &nbsp; |
| Andrea | **Registrazione e login:** realizzare pagine e flussi web, conservando access/refresh token nella sessione server-side. | &nbsp; |
| Andrea | **Sessione e sicurezza:** implementare refresh, logout, scadenza e protezione delle pagine USER/ADMIN; non esporre i token al JavaScript. | &nbsp; |
| Andrea | **Marketplace:** realizzare catalogo e dettaglio con filtri, paginazione, indicazione astabile e quantità disponibile/bloccata. | &nbsp; |
| Andrea | **Portafoglio:** realizzare saldi, impostazione del totale e movimenti paginati; mostrare errori e saldo riservato. Le API sono già disponibili. | &nbsp; |
| Andrea | **Lobby:** realizzare filtri di stato/categoria/testo e countdown basati sul tempo del server. Le API sono già disponibili. | &nbsp; |
| Andrea | **Catalogo ADMIN:** realizzare gestione categorie/prodotti e PUT completo con versione; gestire il 409 rileggendo il prodotto. | &nbsp; |
| Andrea | **Programmazione ADMIN:** realizzare selezione del prodotto disponibile/astabile, input Europe/Rome, prezzo e riepilogo dello stock prenotato. | &nbsp; |
| Andrea | **Stanza — ingresso:** recuperare snapshot e ticket, sottoscrivere topic/coda privata e inviare join; mostrare pre-live e presenza. | &nbsp; |
| Andrea | **Stanza — rilanci:** realizzare form, clientBidId e knownSequence; gestire BID_CONFIRMED/BID_REJECTED e retry senza duplicare il comando. | &nbsp; |
| Andrea | **Stanza — aggiornamenti:** sincronizzare countdown, prezzo, leader, feed e scadenza; mostrare vincitore/importo o chiusura senza offerte. | &nbsp; |
| Andrea | **Stanza — riconnessione:** richiedere nuovo ticket e snapshot, recuperare gap di sequence e ignorare eventi vecchi/duplicati; disabilitare il form durante il riallineamento. | &nbsp; |
| Andrea | **Inventario:** realizzare la pagina dei prodotti posseduti dopo la disponibilità di `GET /me/inventario`. | &nbsp; |
| Andrea | **Vittorie e storico:** realizzare pagine USER e ADMIN dopo le API di Marco, con filtri/paginazione concordati. | &nbsp; |
| Andrea | **Annullamento:** aggiungere il comando ADMIN e gestire l'esito nella stanza dopo l'integrazione di API ed evento. | &nbsp; |
| Andrea | **Acquisto fisso:** realizzare acquisto e aggiornamento di saldo, disponibilità e inventario dopo la disponibilità dell'API. | &nbsp; |
| Andrea | **Verifiche web:** provare ruoli, validazioni, sessione scaduta, Producer indisponibile, due utenti/più schede, saldo insufficiente e riconnessione. | &nbsp; |
| Andrea | **Documentazione Consumer:** aggiornare rotte, flussi e istruzioni di avvio/demo delle pagine effettivamente integrate. | &nbsp; |
| Team | **Inventario — assegnazione:** decidere il responsabile dell'API; la pagina Consumer resta ad Andrea. | &nbsp; |
| Da assegnare | **Inventario — contratto:** definire DTO, paginazione e ordinamento di `GET /me/inventario`. | &nbsp; |
| Da assegnare | **Inventario — API:** leggere quantità e prodotti personali dalle tabelle esistenti, usando l'identità del token e autorizzazione USER. | &nbsp; |
| Da assegnare | **Inventario — test e documentazione:** verificare privacy, lista vuota, quantità dopo una vittoria e paginazione; aggiornare contratto e Postman. | &nbsp; |
| Team | **Acquisti fissi — assegnazione e regole:** decidere il responsabile e concordare quantità, prodotto acquistabile, destinatario dei crediti e comportamento dei retry. | &nbsp; |
| Da assegnare | **Acquisti fissi — contratto:** definire DTO ed errori di `POST /prodotti/{id}/acquisti`, accesso USER ed effetti su stock, saldo, ledger e inventario. | &nbsp; |
| Da assegnare | **Acquisti fissi — servizio:** implementare lock e transazione sulle quattro aree, riusando PortafoglioRepository e aggiornando le versioni. | &nbsp; |
| Da assegnare | **Acquisti fissi — API:** aggiungere controller, validazioni e autorizzazione USER anche sul servizio. | &nbsp; |
| Da assegnare | **Acquisti fissi — test e documentazione:** verificare ultima unità concorrente, saldo insufficiente, prodotto non acquistabile, rollback e retry; aggiornare contratto e Postman. | &nbsp; |
| Team | **Integrazione finale:** verificare il flusso ADMIN programma → USER partecipa/rilancia → chiusura → saldo, inventario e storici, includendo annullamento e acquisto fisso. | &nbsp; |
| Team | **Avvio e consegna:** verificare Producer/Consumer indipendenti e stack Docker healthy; integrare le porzioni completate e aggiornare le relative righe del TODO. | &nbsp; |

## Consegna locale di Marco — 12 ottobre 2026

Tutte le attività di Marco sono completate nella copia di lavoro: annullamento
ADMIN senza offerte, evento post-commit, vittorie personali e storico ADMIN,
con sicurezza, filtri/pagine, test, documentazione e Postman.
Verifica: 268 test Producer e 1 Consumer superati, nessun test saltato.
Dettagli e integrazione per Maikol/Andrea nel
[report di consegna](12-annullamento-storici.md). Le righe degli altri
responsabili restano a loro cura; il relay esistente già trasporta il nuovo
evento e il payload è documentato per la loro verifica.
