# Esercitazione — LiveAuction

Questa è la consegna e descrive il risultato richiesto, incluse funzionalità
ancora da sviluppare. Per lo stato implementato consultare il
[README](README.md) e il [TODO del team](docs/todo.md).

## Obiettivo

Per la consegna corrente l'attivazione SMTP esterna e la ricezione dell'email
in inbox sono escluse dallo scope. Il modulo notifiche già implementato resta
nel progetto e può essere verificato localmente senza un provider esterno.

Realizzare un marketplace con due applicazioni Spring Boot indipendenti.
L'ADMIN gestisce il catalogo e programma aste di prodotti disponibili; gli
utenti entrano nelle stanze live, offrono crediti virtuali e ricevono nel proprio
inventario gli eventuali prodotti vinti.

Il Producer possiede dati e regole. La Consumer presenta l'interfaccia web e non
accede mai direttamente a PostgreSQL.

## Applicazioni

- **producer** sulla porta `8081`: REST API, WebSocket, Security, JPA, Flyway e
  PostgreSQL;
- **consumer** sulla porta `8082`: Thymeleaf, client REST e interfaccia
  LiveAuction.

## Requisiti obbligatori

### Utenti e portafoglio

- Registrazione e login.
- Ruoli `USER` e `ADMIN`.
- Un portafoglio di crediti finti per ogni utente.
- Saldo totale, saldo riservato e saldo disponibile.
- Storico dei movimenti del portafoglio.
- Un'offerta senza saldo disponibile sufficiente viene rifiutata.
- Nessun denaro reale o servizio di pagamento.

### Prodotti

- Catalogo ricercabile e paginato.
- Categorie e SKU univoco.
- Campo esplicito `astabile` per indicare se il prodotto può essere messo
  all'asta.
- Quantità disponibile e quantità bloccata per ogni prodotto.
- L'ADMIN può modificare l'abilitazione alle aste.
- Un prodotto non astabile o senza quantità disponibile non può essere usato
  per programmare un'asta.
- Più aste dello stesso prodotto sono ammesse solo se lo stock copre una unità
  per ciascuna asta.

### Programmazione delle aste

- Solo l'ADMIN può programmare, modificare o annullare un'asta.
- Durante la creazione sceglie prodotto, data e ora di inizio e prezzo iniziale.
- L'orario inserito è interpretato in `Europe/Rome` e salvato come istante UTC.
- La programmazione blocca immediatamente una unità del prodotto.
- La stanza diventa accessibile tre minuti prima dell'inizio.
- Nel pre-live gli utenti possono entrare, ma non possono ancora offrire.

### Asta live

- Durata iniziale: **7 minuti**.
- Ogni offerta valida esegue `endsAt = endsAt + 20 secondi`.
- Il server è l'autorità per inizio, scadenza e validità delle offerte.
- L'importo deve rispettare il prezzo iniziale o il rilancio minimo.
- La migliore offerta riserva i fondi; quando viene superata, i fondi del
  precedente leader vengono liberati.
- Alla chiusura, prodotto e crediti vengono trasferiti atomicamente.
- Il vincitore viene annunciato nella stanza.
- Un'asta senza offerte sblocca l'unità e la restituisce allo stock disponibile.

### Storici e notifica

- Ogni utente vede il proprio storico dei prodotti vinti.
- L'ADMIN vede lo storico globale con aste, vincitori e importi finali.
- Dopo il commit della chiusura, il sistema invia al vincitore una email con
  prodotto, asta, importo e data. Un errore email non annulla la vittoria.

### Esperienza live

- Stanza WebSocket in stile gioco.
- Stati visibili: programmata, pre-live, live, conclusa e annullata.
- Timer calcolato sul tempo del server.
- Feed offerte, miglior offerente, prezzo corrente e partecipanti connessi.
- Riconnessione con recupero dello snapshot REST.
- Nessun aggiornamento del timer ogni secondo dal server: il client interpola
  localmente da `serverTime` ed `endsAt`.

## Vincoli tecnici

- Java 21, Spring Boot 3, PostgreSQL e Flyway.
- Spring Security e password hashate.
- Spring WebSocket/STOMP.
- DTO separati dalle entity.
- `BigDecimal`/`NUMERIC`, mai `double`, per crediti e prezzi.
- `Instant`/`TIMESTAMPTZ` nel dominio persistito; `LocalDateTime` viene usato
  soltanto come input UI insieme alla zona `Europe/Rome`.
- Programmazione, offerta e chiusura sono transazionali.
- Lock delle righe asta, prodotto e portafogli per evitare overselling e doppie
  vittorie.
- Eventi WebSocket ed email pubblicati solo dopo il commit.
- Errori REST in `application/problem+json`.
- Docker Compose con profilo `prod` per l'intero stack.

## Pagine minime

- `/login` e `/registrazione`;
- `/marketplace`;
- `/prodotti/{id}`;
- `/aste`;
- `/aste/{id}`;
- `/inventario`;
- `/me/vittorie`;
- `/impostazioni/portafoglio`;
- `/admin/prodotti`;
- `/admin/aste/nuova`;
- `/admin/aste/storico`.

## Criteri di accettazione principali

1. Solo l'ADMIN può programmare un'asta e sceglie sempre il prezzo iniziale.
2. Una unità viene bloccata al momento della programmazione.
3. Un prodotto con `astabile = false` o stock disponibile pari a zero viene
   rifiutato.
4. La stanza è accessibile esattamente da tre minuti prima dell'inizio.
5. Nel pre-live nessuna offerta viene accettata.
6. L'asta parte all'orario programmato e dura inizialmente sette minuti.
7. Ogni offerta valida aggiunge esattamente venti secondi a `endsAt`.
8. Due offerte simultanee non generano due leader o vincitori.
9. Il saldo disponibile non può diventare negativo.
10. Il precedente leader recupera immediatamente i crediti riservati.
11. La chiusura trasferisce una sola volta crediti e prodotto.
12. Il vincitore appare nello storico personale e nello storico globale ADMIN.
13. Dopo una riconnessione, lo snapshot REST riallinea la stanza.
14. La Consumer non contiene repository JPA né credenziali PostgreSQL.

La progettazione completa e i payload di riferimento sono in [`docs/`](docs/README.md).
