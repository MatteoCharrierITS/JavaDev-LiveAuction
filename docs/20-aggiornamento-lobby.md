# Aggiornamento automatico Auctions — 21 ottobre 2026

## Difetto e correzione

La lobby `/aste` caricava i dati soltanto all'apertura. Il JavaScript faceva
scorrere il countdown ma non aggiornava prezzi, offerte e stati: a zero restava
su `Awaiting server update`. Il WebSocket era ed è riservato alle singole stanze.

Ora la lobby richiede ogni 5 secondi `GET /aste?partial=true`, mantenendo i
parametri applicati `stato`, `categoria`, `query`, `page` e `size`. Il Consumer
usa il GET REST pubblico del Producer e rende il frammento Thymeleaf dei soli
risultati, inclusa la paginazione. Nessun token Bearer raggiunge il browser.
Il frammento mantiene le regole di visibilità USER/ADMIN e i form con CSRF.
Risposta `no-store`; nessuna ricarica completa, modifica dei filtri in corso o
deduzione client dello stato dell'asta. Countdown riallineato al tempo Producer.

Al raggiungimento dello zero viene richiesta una verifica anticipata, senza
tempeste di richieste. Una chiusura realmente bloccata nel Producer non viene
inventata dal client: lo stato resta quello restituito dal dominio.

Errori: dati precedenti conservati con avviso, retry a 10 secondi; timeout a 8.
Polling sospeso in background e sostituzione sospesa durante il focus su link
o comandi dei risultati. Uscita pagina abortisce le richieste; ritorno da cache
di navigazione riattiva il polling. Il WebSocket della stanza è invariato.

## Verifiche

70 test Java Consumer e 29 JavaScript superati. Inclusi frammento pubblico,
parametri/paginazione, stati chiusa/annullata, link USER, annullamento ADMIN con
CSRF, indisponibilità upstream, errori di rete, ripresa, focus e richieste singole.
Build verify riuscita. Nessuna modifica a Producer, schema o migrazioni.
Le collection Postman Producer restano valide: il parametro nuovo riguarda
solo il rendering HTML del Consumer, non un nuovo contratto REST Producer.

Collaudo browser con Producer simulato su loopback e Consumer su porta 18082:
verificati prezzo 60→75 CRD, offerte 2→3 e stato APERTA→CHIUSA senza reload;
il testo non ancora applicato nel filtro ricerca e il suo focus restano intatti.
nessun account, acquisto, asta o saldo del database ufficiale viene modificato.
I servizi ufficiali restano spenti; il prossimo avvio compila il Consumer nuovo.
