# LiveAuction — motore live

Questo documento descrive il motore completo previsto. Programmazione,
attivazione temporale, lobby, snapshot, servizi di rilancio/settlement e trasporto
WebSocket sono implementati. Il branch corrente collega la chiusura automatica
allo scheduler e al recupero dopo un riavvio. Restano da completare l'attivazione
SMTP dell'email (modulo integrato in `main` con coda e retry), annullamento,
storici e UI. Vedere lo
[stato del modulo aste](README.md#stato-del-modulo-aste).
I nomi `startsAt` e `endsAt` usati nei diagrammi corrispondono ai campi
`inizioAt` e `fineAt` delle API implementate.

## Timeline

Per un'asta con `startsAt = 18:30 Europe/Rome`:

```text
18:27  PROGRAMMATA → STANZA_APERTA
       join consentito, offerte vietate

18:30  STANZA_APERTA → APERTA
       endsAt iniziale = 18:37

18:34  offerta valida
       endsAt = 18:37:20

18:37:20  APERTA → CHIUSA, salvo ulteriori rilanci
```

Le date mostrate all'utente sono italiane, mentre API e database usano UTC.

### Attivazione automatica implementata

`AstaScheduler` controlla ogni secondo le aste da attivare e recupera quelle
arretrate all'evento `ApplicationReadyEvent`. Per ogni asta chiama
`AstaLifecycleService.aggiornaStato`, che apre una transazione, blocca la riga
con `PESSIMISTIC_WRITE` e legge il tempo UTC dopo l'acquisizione del lock.

- A `inizioAt - 3 minuti` passa a `STANZA_APERTA`.
- A `inizioAt` passa a `APERTA`, con `fineAt = inizioAt + 7 minuti`.
- Ogni transizione incrementa `sequence` una sola volta; cicli ripetuti o
  job concorrenti non duplicano le transizioni.
- Se al riavvio l'inizio è già passato, recupera entrambe le transizioni nella
  stessa transazione e conserva la scadenza originale.
- `AstaLifecycleService` non modifica le aste già `APERTA`, `CHIUSA` o
  `ANNULLATA`; una scadenza estesa dalle offerte non viene azzerata. Lo scheduler
  tratta le `APERTA` scadute nella fase separata di chiusura descritta sotto.

Un errore su una singola asta non ferma le altre; un errore di lettura o di
transizione viene ritentato al ciclo successivo. L'intervallo si configura con
`ASTE_SCHEDULER_INTERVAL_MS` (predefinito `1000`), e
`ASTE_SCHEDULER_ENABLED=false` disattiva il job.

La chiusura e il settlement appartengono al modulo offerte/chiusura. Se il
riavvio avviene dopo `fineAt`, il recupero porta l'asta fino a `APERTA` con una
scadenza già trascorsa. Nello stesso ciclo il job seleziona le aste `APERTA` con
`fineAt <= serverTime` e richiama `ChiusuraAstaService`; il servizio ricontrolla
stato e scadenza sotto lock, quindi non riapre il timer né duplica il settlement.
Un errore isolato di chiusura viene ritentato al ciclo successivo e non impedisce
di processare le altre aste.

## Programmazione sicura

Pseudoflusso del comando ADMIN:

```text
BEGIN
  valida ruolo, data/ora e prezzo iniziale
  converti Europe/Rome → Instant UTC
  lock prodotto
  verifica astabile e quantita_disponibile > 0
  decrementa disponibile e incrementa bloccata
  crea asta PROGRAMMATA con startsAt e endsAt = startsAt + 7 minuti
COMMIT
```

Il lock impedisce che due richieste usino contemporaneamente l'ultima copia.

Nel Producer, `asta.repos.ProdottoAstaRepository` legge il prodotto con
`PESSIMISTIC_WRITE`. `AstaService.bloccaProdottoPerProgrammazione` richiede ADMIN
e una transazione già attiva (`Propagation.MANDATORY`): il lock deve restare
attivo fino all'aggiornamento dello stock e al salvataggio dell'asta nella stessa
transazione. Il metodo verifica astabilità e disponibilità senza modificare
le quantità.

`AstaService.programmaAsta` apre la transazione, verifica che l'ID ADMIN
corrisponda al `sub` del token e che l'utente sia attivo con ruolo ADMIN.
Valida prezzo e orario, blocca il prodotto, sposta una unità da disponibile a
bloccata e salva l'asta `PROGRAMMATA` con scadenza iniziale a sette minuti.
Il prodotto è un'entity gestita da JPA: il flush dell'asta salva anche lo stock.
Un errore provoca il rollback di entrambe le modifiche.

## Timer autorevole

Il browser calcola i countdown usando `serverTime`, `startsAt` ed `endsAt`.
Riceve nuovi riferimenti alle transizioni e dopo ogni offerta. Il timer visuale
può arrivare a zero, ma solo il Producer cambia lo stato dell'asta.

A ogni rialzo accettato:

```text
endsAt = endsAt + 20 secondi
```

Non si usa “venti secondi da adesso”: si estende sempre la scadenza corrente.

## Sezione critica di PLACE_BID

```text
BEGIN
  lock asta
  se non APERTA o scaduta: rifiuta
  valida utente, importo e clientBidId
  lock wallet nuovo offerente e precedente leader in ordine di id
  verifica saldo disponibile
  riserva nuovo importo
  libera precedente importo
  salva offerta
  aggiorna leader, prezzo, endsAt +20s e sequence
COMMIT
pubblica BID_ACCEPTED
```

Ordinare i lock dei portafogli per id riduce i deadlock. In caso di conflitto,
la transazione può essere ritentata un numero limitato di volte.

## Settlement

Con vincitore:

1. consuma la riserva del vincitore;
2. accredita il conto amministrativo;
3. decrementa la quantità bloccata del prodotto;
4. assegna una unità all'inventario del vincitore;
5. scrive i movimenti ledger;
6. salva vincitore, prezzo finale e `chiusaAt`;
7. incrementa `sequence` e committa;
8. pubblica `AUCTION_CLOSED` con il vincitore;
9. richiede l'email riepilogativa.

Senza offerte, la quantità passa da bloccata a disponibile. Lock e controllo
dello stato rendono la chiusura idempotente.

## Protocollo eventi

Le transizioni temporali producono `asta.events.AstaTransizioneEvent` con
`type` (`ROOM_OPENED` o `AUCTION_STARTED`), `auctionId`, `sequence`,
`serverTime`, `stato`, `aperturaStanzaAt`, `inizioAt` e `fineAt`. Sono eventi
Spring interni pubblicati da `ApplicationEventPublisher` dopo il commit; un
rollback non produce eventi. Il modulo WebSocket li ascolta con
`@EventListener` e li invia a `/topic/aste/{id}` tramite `AstaEventRelay`.
Si tratta di notifiche in memoria: il client dovrà
recuperare lo snapshot in caso di disconnessione o gap di `sequence`.

Ogni evento pubblico contiene almeno:

```json
{
  "type": "BID_ACCEPTED",
  "auctionId": 42,
  "sequence": 9,
  "serverTime": "2026-10-03T16:34:12Z"
}
```

Se il client possiede `sequence=7` e riceve `9`, passa a
`SINCRONIZZAZIONE…` e recupera lo snapshot REST.

## Sicurezza

- Solo ADMIN sugli endpoint di programmazione e storico globale.
- Ticket WS monouso, legato a utente e asta, TTL 30 secondi.
- Ticket non emesso prima di `startsAt - 3 minuti`.
- Autorizzazione verificata su ogni comando, non solo al connect.
- Offerte accettate soltanto nello stato `APERTA`.
- Rate limit indicativo previsto, non ancora implementato: 5 comandi offerta
  al secondo per utente/asta.
- Importo rifiutato se scala o precisione non sono valide.
- Username mascherato negli eventi pubblici.

## Demo

1. L'ADMIN abilita un prodotto con tre unità e programma un'asta scegliendo
   prezzo iniziale e data/ora.
2. Mostra che disponibile diminuisce e bloccata aumenta.
3. Due utenti entrano nei tre minuti di pre-live senza poter offrire.
4. All'orario previsto l'asta parte con sette minuti.
5. I due utenti rilanciano e vedono prezzo, riserve e `+20s` in tempo reale.
6. Un'offerta oltre il saldo disponibile viene rifiutata.
7. Alla chiusura appare il vincitore e il prodotto entra nel suo inventario.
8. La vittoria compare nello storico personale e nello storico globale ADMIN.
9. Viene generata l'email riepilogativa.
