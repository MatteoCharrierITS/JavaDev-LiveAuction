# Inventario e acquisti fissi — 14 ottobre 2026

Base `main` `ede0201`, modifiche nella copia di lavoro, senza commit.
Contratto autorevole: [REST](04-api-rest.md). La manutenzione dei nuovi domini
resta da assegnare dal team: non sono state attribuite nuove aree ai colleghi.

## Decisioni confermate dal richiedente

- Acquisto riservato a USER, quantità intera da 1 a 100.
- Incasso a un ADMIN attivo scelto dall'ambiente, non dal body del compratore.
- UUID `clientPurchaseId` obbligatorio: un retry identico restituisce l'esito
  originario, senza nuovi effetti; UUID riusato con dati diversi dà conflitto.
- Prezzo corrente del catalogo letto sotto lock; prodotto e categoria attivi,
  prezzo fisso presente, stock disponibile e saldo disponibile sufficienti.
  `astabile` non impedisce un acquisto fisso. Stock bloccato e fondi riservati
  alle aste non possono essere consumati.

## Implementato

`GET /api/v1/me/inventario` restituisce soltanto i prodotti del chiamante con
quantità positiva, inclusi quelli non più pubblici. Paginazione 0-based,
dimensione 1–100, ordinamento per ID prodotto, lettura `REPEATABLE_READ` e DTO
senza dati privati. La pagina Consumer `/inventario` mostra quantità, SKU,
categoria, lista vuota, paginazione e indisponibilità del catalogo pubblico.

`POST /api/v1/prodotti/{id}/acquisti` restituisce `201` al primo acquisto e
`200` al retry, con `duplicato=true`. Un'unica transazione registra acquisto,
addebito USER, incasso ADMIN, due movimenti di ledger, decremento della sola
disponibilità e incremento dell'inventario. I lock riusano il repository del
portafoglio e sono compatibili con aste e programmazione concorrenti.
Un fallimento annulla tutte le scritture. Non vengono pubblicati eventi di
asta per un acquisto fisso e non viene inviata l'email riservata alle vittorie.

La UI contiene il form nel dettaglio prodotto, conferma tramite POST protetto
da CSRF e accesso USER. Dopo il successo apre l'inventario aggiornato. In caso
di timeout/5xx conserva UUID e quantità nel pulsante di retry: non trasforma un
esito incerto in un secondo acquisto. Saldo e stock vengono riletti nelle loro
pagine dal Producer, senza cache locale del dominio.

V13 crea `acquisti_fissi`; V14 estende il vincolo del ledger con `INCASSO_FISSO`.
Le migrazioni già applicate non sono state riscritte e non vengono inseriti
account o prodotti demo. Corretto inoltre il feed della stanza: gli eventi
vecchi/duplicati già coperti dallo snapshot vengono scartati prima di entrare
nella cronologia.

## Configurazione locale

1. Registrare un account e promuovere esplicitamente quello scelto ad ADMIN,
   seguendo il README. Non viene scelto automaticamente il primo ADMIN.
2. Impostare `SHOP_ADMIN_ID=<id-admin>` nell'ambiente del Producer o nel `.env`
   letto da Compose; riavviare il Producer. L'IDE non legge `.env` da solo.
3. Senza configurazione, con ID inesistente o non ADMIN attivo, l'acquisto
   restituisce `503 ACQUISTI_NON_CONFIGURATI` senza effetti. Default `0`.
4. Il USER imposta i crediti virtuali dal portafoglio; l'ADMIN crea prodotto,
   categoria, prezzo fisso e disponibilità. Non occorre SMTP esterno.

Il Consumer tratta timeout/5xx come esito potenzialmente incerto: verificare
prima la configurazione del Producer se il retry continua a fallire. Non
generare un nuovo UUID per verificare un acquisto dal risultato sconosciuto.

## Verifiche eseguite

`clean verify` dalla radice, Temurin 21.0.11, PostgreSQL 16 in un container
dedicato, `RUN_DB_TESTS=true`, `RUN_WS_TESTS=true`, scheduler disabilitato solo
nella suite: **293 test Producer e 52 Consumer superati**, nessun fallimento,
errore o test saltato; entrambi i JAR prodotti. **12 test JavaScript superati**.
Mockito 5.17.0 caricato come agente esplicito.

Coperti isolamento inventario, paginazione/ruoli, prodotti inattivi, quantità
aggregate dopo due vittorie dello stesso prodotto; acquisto/ledger/incasso,
retry dopo modifica catalogo/configurazione, UUID con dati diversi, quantità
invalide anche frazionarie, fondi riservati, stock bloccato, categoria inattiva,
prezzo assente, rollback per overflow ADMIN, retry concorrenti, ultima unità
contesa e acquisto concorrente con programmazione asta. Test Consumer per
sicurezza/CSRF, lista vuota, rendering, errori e retry con lo stesso UUID.

Prova reale con JAR avviati su localhost e database separato `liveauction_browser`:

- Login e inventario vuoto; acquisto dalla UI di 2 unità a 12,50 CRD:
  inventario 2, saldo USER 100→75, movimento 25 e incasso ADMIN 25.
  Ripetuto lo stesso UUID via HTTP: `200`, `duplicato=true`, saldo e quantità
  invariati. Dopo il cambio utente, verificata nella UI l'unità del vincitore
  e la relativa vittoria, senza vedere le due unità del primo compratore.
  Provata anche l'impostazione del saldo a zero dalla UI: acquisto rifiutato
  con messaggio di saldo insufficiente; saldo poi ripristinato tramite il
  medesimo form, con i relativi movimenti di impostazione.
- Programmazione ADMIN via REST, pre-live e ingresso dalla UI, ticket/STOMP,
  presenza e apertura automatica; primo rilancio dalla UI a 5 e secondo utente
  via client STOMP reale a 6. Prezzo aggiornato e riserva del primo USER liberata.
- Chiusura automatica: annuncio del vincitore e form disabilitato nella UI;
  saldo del vincitore 94, riserva zero, inventario 1; saldo ADMIN 31
  (25 dall'acquisto + 6 dall'asta); disponibilità prodotto 2, bloccato zero.
  Per abbreviare l'attesa della sola chiusura sono stati anticipati in SQL gli
  orari della fixture nel DB temporaneo, rispettando il vincolo dei 7 minuti:
  nessuna modifica all'orologio o alle regole dell'applicazione.

Il database di sviluppo e i suoi volumi non sono stati usati. Le fixture sono
solo dati sintetici e non vengono aggiunte alle migrazioni.

Stack Docker `prod` compilato dai Dockerfile su Java 21 e avviato con Compose
in un progetto separato: nomi container dedicati, porte casuali solo su
127.0.0.1, volume di prova distinto e chiave JWT sintetica. Tutti e tre i
servizi `healthy`, readiness Producer `UP`, Flyway V13/V14 applicate. Login
dalla UI su localhost con cookie Secure, acquisto di 1 unità a 10 CRD:
inventario 1, saldo compratore 50→40, incasso ADMIN 10, stock 3→2.
Il WebSocket era già provato sui JAR host; nello stack Docker a porte casuali
non è stato ripetuto il flusso live, che richiede URL/origini coerenti.
I servizi di prova sono stati arrestati e i soli dati temporanei rimossi al termine;
nessun container o volume preesistente del progetto viene eliminato.

## Limiti del collaudo

La prova a due utenti usa un browser più un client STOMP, non due browser
indipendenti. La matrice manuale completa (più schede, rete interrotta,
cookie/sessioni, dimensioni schermo e riavvio durante la demo) non è certificata
da questa prova; le relative righe nel TODO restano aperte. Le integrazioni
automatiche coprono numerosi casi di errore, concorrenza e recupero.
Le collection Postman sono aggiornate e validate come JSON/script, ma non è
stato eseguito il runner Postman. Gli acquisti manuali sono le richieste 11–12
di `prodotti.json`, distinte dal runner catalogo ADMIN 01–10.
