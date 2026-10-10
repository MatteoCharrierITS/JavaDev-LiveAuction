# Interfaccia e flussi utente

## Rotte Consumer

| Rotta | Accesso | Pagina |
| --- | --- | --- |
| `/login` | pubblico | accesso |
| `/registrazione` | pubblico | nuovo account |
| `/account` | autenticato | account e richiesta di eliminazione |
| `/marketplace` | pubblico | catalogo completo |
| `/prodotti/{id}` | pubblico | dettaglio, stock e astabilità |
| `/aste` | pubblico | lobby |
| `/aste/{id}` | USER | stanza LiveAuction |
| `/inventario` | USER | prodotti posseduti |
| `/me/vittorie` | USER | storico personale vittorie |
| `/impostazioni/portafoglio` | USER | saldo finto e ledger |
| `/admin/prodotti` | ADMIN | catalogo, stock e flag astabile |
| `/admin/aste/nuova` | ADMIN | programmazione asta |
| `/admin/aste/storico` | ADMIN | storico globale e vincitori |

## Catalogo

Il marketplace mostra solo prodotti attivi di categorie attive. Il pannello
ADMIN consulta anche gli elementi disattivati. Il form di modifica prodotto
invia tutti i campi modificabili e la `versione` dell'ultima lettura; in caso di
`409 VERSIONE_NON_AGGIORNATA` ricarica il prodotto prima di riproporre la modifica.
La quantità bloccata è visualizzata ma non modificabile nel form.

Ogni card indica testualmente:

- `DISPONIBILE ALL'ASTA` oppure `NON ASTABILE`;
- quantità disponibile;
- numero di aste programmate o live.

Il pannello ADMIN permette di modificare `astabile` e stock. Il pulsante
“Programma asta” è attivo soltanto con `astabile = true` e almeno una unità
disponibile; se è disabilitato, la UI ne mostra il motivo.

## Programmazione ADMIN

Il form contiene:

- prodotto selezionabile tra quelli astabili e disponibili;
- data e ora di inizio tramite controllo `datetime-local`;
- indicazione fissa del fuso `Europe/Rome`;
- prezzo iniziale in CRD;
- riepilogo: apertura stanza `-3 min`, durata `7 min`, `+20s` per rilancio.

Dopo la conferma, una unità risulta bloccata e l'asta appare nella lobby.

## Lobby

Filtri: stato, categoria, testo, “in partenza” e “a cui partecipo”. Ogni card
mostra prezzo iniziale o corrente, numero offerte e uno di questi countdown:

- `La stanza apre tra…` per `PROGRAMMATA`;
- `L'asta inizia tra…` per `STANZA_APERTA`;
- `Termina tra…` per `APERTA`;
- vincitore o `Nessuna offerta` per `CHIUSA`.

## Stanza LiveAuction

```text
┌──────────────────────────────────────────────────────────────────┐
│ ● LIVE  Laptop Pro 15                         04:42 +20s/rialzo  │
├───────────────────────────────┬──────────────────────────────────┤
│                               │ OFFERTA ATTUALE                  │
│        immagine/prodotto      │ 630,00 CRD                       │
│                               │ leader: g***i                    │
│ Prezzo iniziale: 500 CRD      │                                  │
│ Inizio: 18:30 Europe/Rome     │ [ 631,00 ] [ RIALZA ]            │
├───────────────────────────────┼──────────────────────────────────┤
│ IL TUO PORTAFOGLIO            │ LIVE FEED                        │
│ disponibile  8.750 CRD        │ 18:34 g***i → 630 CRD (+20s)   │
│ riservato     1.250 CRD       │ 18:33 l***a → 620 CRD (+20s)   │
└───────────────────────────────┴──────────────────────────────────┘
```

Durante `STANZA_APERTA` lo stesso layout mostra utenti presenti e countdown
all'inizio, ma il form delle offerte è disabilitato. Al termine compare un
annuncio evidente con vincitore e importo finale, oppure “Asta conclusa senza
offerte”.

Stati connessione: `LIVE`, `RICONNESSIONE…`, `SINCRONIZZAZIONE…`. Dopo la
riconnessione il form resta disabilitato finché non arriva uno snapshot valido.
Gli ultimi venti secondi sono evidenziati rispettando `prefers-reduced-motion`.

## Flussi principali

### Programmare

Login ADMIN → prodotti → verifica `astabile` e quantità → data/ora e prezzo
iniziale → riepilogo → conferma → unità bloccata → asta in lobby.

### Partecipare

Login USER → lobby → apertura stanza tre minuti prima → ticket WS → join →
attesa → evento `AUCTION_STARTED` → rilanci → chiusura e annuncio vincitore.

### Vincere

Chiusura → addebito crediti → prodotto nell'inventario → voce in “Le mie
vittorie” → email riepilogativa post-commit.

### Riconnettersi

WebSocket perso → indicatore offline → nuovo ticket → subscribe → snapshot REST
→ confronto `sequence` → riabilitazione comandi solo se l'asta è `APERTA`.

### Consultare lo storico ADMIN

Login ADMIN → storico aste → filtri → dettaglio con prodotto, vincitore, prezzo
iniziale, prezzo finale, date e numero di offerte.
