# Aggiornamento UI — 20 ottobre 2026

Il Consumer adotta un tema scuro con accenti viola, catalogo a schede,
presentazione iniziale, categorie e immagini illustrative. Riferimento visivo:
[GamingGear su Pinterest](https://it.pinterest.com/pin/633952085026333607/).
L'interfaccia conserva la lingua inglese già presente e i dati del catalogo.

Navigazione con pagina attiva, menu mobile utilizzabile da tastiera, collegamento
per saltare al contenuto, focus visibile e rispetto delle animazioni ridotte.
Account con collegamenti pertinenti al ruolo ed eliminazione in una sezione
espandibile. Aggiornati anche login, dettaglio, inventario, aste e pagine ADMIN.

Il dettaglio mostra una stima del totale e lo stato di invio del modulo;
prezzo, disponibilità e addebito rimangono validati dal Producer. Non cambiano
API, schema, ruoli, CSRF, token server-side o UUID degli acquisti idempotenti.
Il WebSocket continua a usare il protocollo e la sincronizzazione esistenti.

## Immagini

Quattro immagini dimostrative provengono dall'API pubblica
[DummyJSON](https://dummyjson.com/docs/products): prodotti 101 (cuffie),
79 (notebook), 47 (lampada), 175 (zaino). Sono salvate come WebP sotto
`consumer/src/main/resources/static/images/products/`; le altre illustrazioni
SVG sono locali. Nessuna chiamata all'API esterna durante la navigazione.
Le immagini sono illustrative, non fotografie garantite dei prodotti in vendita;
non è introdotto un servizio di upload. Verificare le licenze prima di un uso
diverso dal prototipo didattico locale.

## Collaudo

Superati 63 test Java Consumer e 18 test JavaScript, inclusi quelli della stanza
live; build `verify` riuscita. Verificati nel browser layout desktop e mobile
390 px, menu, login, filtri e acquisto con aggiornamento dell'inventario.
L'acquisto di prova ha consumato crediti virtuali e stock della demo normalmente:
non sono stati azzerati saldi o ledger. I controlli su PC fisici distinti e la
matrice dei guasti di rete restano a carico del collaudo condiviso.

Avvio e credenziali rimangono quelli della [demo multi-PC](15-demo-multipc.md).
Al termine della sessione l'indirizzo LAN è cambiato in `192.168.137.99`:
UI `http://192.168.137.99:8082`, WebSocket `ws://192.168.137.99:8081/ws`.
Aggiornati URL e origini consentite nel `.env` locale e riavviati i due servizi;
il vecchio indirizzo `10.135.188.192` non è più quello attivo. Un ulteriore cambio
di rete richiede di riallineare questi valori prima delle prove da altri PC.
Nota successiva: la rete è poi tornata su `10.135.188.192`; configurazione
corretta e endpoint automatico introdotto nel [report WebSocket](17-correzione-ws-lan.md),
che aggiorna le indicazioni di rete riportate sopra.
Email disabilitate. Un riavvio del solo Consumer può richiedere un nuovo login,
senza cancellare account, acquisti o aste persistiti nel Producer.
