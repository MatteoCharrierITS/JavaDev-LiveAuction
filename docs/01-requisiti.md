# Requisiti e regole di dominio

## Attori

- **Visitatore**: consulta catalogo e aste pubbliche.
- **Utente**: entra nelle stanze, offre crediti e consulta inventario e vittorie.
- **ADMIN**: gestisce catalogo, stock, abilitazione `astabile`, programmazione e
  storico globale delle aste.

Soltanto l'ADMIN può creare, modificare o annullare un'asta. Non esiste il
flusso nel quale un utente mette in vendita un proprio prodotto.

## Prodotti e stock

Il catalogo pubblico mostra soltanto prodotti attivi appartenenti a categorie
attive. Disattivare una categoria nasconde anche il dettaglio pubblico dei suoi
prodotti (404), senza modificarne stock, flag `attivo` o aste già programmate.
L'ADMIN continua a consultare e modificare questi prodotti. Riattivare la
categoria rende nuovamente visibili i prodotti che sono ancora attivi.
La disattivazione è una regola di visibilità del catalogo, non un annullamento
delle aste e non una modifica dei requisiti di programmazione.

Ogni prodotto contiene i campi:

- `astabile`: autorizza o vieta l'uso del prodotto nelle aste;
- `quantita_disponibile`: unità libere per acquisti o nuove aste;
- `quantita_bloccata`: unità riservate da aste non ancora concluse.

Quando l'ADMIN programma un'asta, il Producer blocca una unità con un'unica
transazione:

```text
quantita_disponibile -= 1
quantita_bloccata += 1
```

L'operazione è rifiutata se `astabile = false` o se
`quantita_disponibile = 0`. Tre copie disponibili permettono al massimo tre aste
contemporaneamente programmate; una sola copia ne permette una sola.

Una vittoria sposta l'unità bloccata nell'inventario del vincitore. Se l'asta
termina senza offerte oppure viene annullata prima di riceverne, l'unità torna
disponibile.

## Portafoglio

- Valuta virtuale: `CRD` (crediti), visualizzata come `1.250,00 CRD`.
- Ogni utente dispone di saldo totale, riservato e disponibile.
- `saldoDisponibile = saldoTotale - saldoRiservato`.
- Ogni modifica produce un movimento di ledger.
- Nessun saldo può essere negativo.
- Un'offerta superiore al saldo disponibile viene rifiutata senza effetti.

## Programmazione

Per creare un'asta l'ADMIN deve indicare:

- prodotto;
- data e ora locale di inizio;
- prezzo iniziale positivo.

L'interfaccia usa `LocalDateTime` in `Europe/Rome`. Il Producer valida l'orario,
lo converte in `Instant` e lo salva in PostgreSQL come `TIMESTAMPTZ` UTC. Il
tempo del server rimane sempre autorevole.

## Ciclo di vita dell'asta

```text
PROGRAMMATA → STANZA_APERTA → APERTA → CHIUSA
      └──────────────┴──────────┴────→ ANNULLATA
```

- `PROGRAMMATA`: stanza non ancora accessibile.
- `STANZA_APERTA`: da tre minuti prima dell'inizio; join consentito, offerte
  vietate.
- `APERTA`: dall'orario programmato; offerte consentite per sette minuti
  iniziali.
- `CHIUSA`: asta terminata, con o senza vincitore.
- `ANNULLATA`: asta cancellata dall'ADMIN quando le regole lo consentono.

Alla transizione in `APERTA`:

```text
endsAt = startsAt + 7 minuti
```

Ogni offerta accettata esegue:

```text
endsAt = endsAt + 20 secondi
```

L'estensione si somma sempre alla scadenza corrente, non a venti secondi dal
momento del rilancio.

## Regole di offerta

1. L'asta deve essere `APERTA` e non scaduta secondo il tempo del server.
2. L'offerente deve essere autenticato, attivo e avere ruolo `USER`.
3. La prima offerta deve essere almeno pari al prezzo iniziale.
4. Le successive devono raggiungere `offertaCorrente + incrementoMinimo`.
5. Il miglior offerente non può rilanciare su sé stesso.
6. Il saldo disponibile deve coprire l'importo.
7. Ogni comando usa un `clientBidId` UUID per l'idempotenza.
8. Asta e portafogli coinvolti vengono bloccati nella transazione.
9. Nuova riserva e rilascio della precedente avvengono atomicamente.
10. L'evento live viene pubblicato soltanto dopo il commit.

Una `knownSequence` arretrata non invalida un'offerta che rispetta lo stato
corrente; il risultato richiede il riallineamento del client. Una sequenza futura
viene rifiutata. Un UUID reinviato con asta, utente o importo diversi è un conflitto.

L'incremento minimo usa il valore applicativo predefinito di `1,00 CRD`; potrà
essere reso configurabile dall'ADMIN senza modificare il modello dati.

## Chiusura e vincitore

Con almeno una offerta:

- la riserva del vincitore viene consumata dal saldo totale;
- i crediti sono accreditati al conto amministrativo;
- l'unità bloccata viene rimossa dallo stock e aggiunta all'inventario del
  vincitore;
- vengono creati i movimenti di portafoglio correlati;
- l'asta salva vincitore, prezzo finale e istante di chiusura;
- il vincitore viene annunciato nella stanza;
- la vittoria appare nello storico personale e nello storico globale ADMIN.

Senza offerte, l'unità viene sbloccata. La chiusura è idempotente: eseguirla più
volte non duplica trasferimenti, addebiti o assegnazioni.

Il conto amministrativo destinatario dell'incasso è quello di `aste.admin_id`.

## Eliminazione dell'account durante un'asta

L'eliminazione ritira le offerte dell'utente nelle aste non concluse, conservando
lo storico. Se è leader, libera la riserva e ripristina la migliore offerta
precedente di un USER attivo con fondi sufficienti, riservandoli nuovamente.
In assenza di candidati coperti, leader e prezzo corrente diventano null.
Le estensioni già concesse rimangono; il ripristino non aggiunge tempo.
L'operazione è atomica con l'anonimizzazione. Le aste sono elaborate in ordine
di ID senza riutilizzare crediti già riservati per un precedente ripristino.
Le aste già concluse mantengono vincitore e offerte storiche.

## Email al vincitore

Dopo il commit viene richiesto l'invio di una email contenente almeno prodotto,
identificativo asta, importo vincente e data di conclusione. L'invio è
asincrono/best effort: un errore del provider viene registrato e ritentato, ma
non annulla la vittoria già conclusa.

## Requisiti non funzionali

- Il client usa l'orologio solo per la visualizzazione.
- Snapshot ed eventi contengono `serverTime`, `startsAt` ed `endsAt` in UTC ISO
  8601.
- Il client recupera uno snapshot dopo riconnessione o gap di `sequence`.
- Ogni evento stanza ha una `sequence` crescente per asta.
- Password hashate con BCrypt o Argon2.
- Token e password mai nei log.
- Paginazione massima 100 elementi.
- Importi con due decimali e arrotondamento esplicito.
