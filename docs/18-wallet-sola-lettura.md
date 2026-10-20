# Wallet di sola lettura — 20 ottobre 2026

## Nuova regola

Ogni nuovo USER riceve alla registrazione 10.000 CRD una sola volta. Il wallet
mostra soltanto saldi e movimenti; rimosse impostazione, incremento e riduzione
manuali del saldo. Rimosso il servizio applicativo di modifica e il relativo
client Consumer, non solo il modulo HTML. Il vecchio PUT Producer e il POST
Consumer sono esplicitamente negati con 403 agli utenti autenticati.

Configurazione nel Producer, in `src/main/resources/application.yaml`:

```yaml
app:
  portafoglio:
    saldo-iniziale: ${WALLET_INITIAL_BALANCE:10000.00}
```

Cambiare il valore predefinito nel YAML oppure impostare `WALLET_INITIAL_BALANCE`
e riavviare. Per Docker Compose l'ambiente del Producer inoltra questa variabile
con default 10000.00: personalizzarla nel .env o nel blocco environment del YAML
Compose. Valori non numerici, negativi, oltre 9999999999.99 o con più di due
decimali impediscono l'avvio. Zero è consentito, senza movimento di importo zero.

Utente, wallet e movimento `CREDITO_INIZIALE` sono nella stessa transazione;
riserva iniziale zero, asta null. Il wallet non può essere ricreato per ripetere
l'accredito. Login, refresh, lettura e cambi di configurazione non attribuiscono
nuovi crediti ai vecchi account.

V15 aggiunge il tipo di movimento mantenendo leggibili i vecchi
`IMPOSTAZIONE_SALDO`; nessuna migrazione precedente modificata e nessun
aggiornamento retroattivo dei saldi. Rilanci, riserve, pagamenti, incassi,
acquisti e ledger continuano a funzionare. Nessuna modifica a payload STOMP.

## Verifiche e pubblicazione

- Build verify Producer: 298 test superati, nessuno saltato, con PostgreSQL
  separato dalla demo, WebSocket e SMTP simulato nei test.
- Build verify Consumer: 62 test superati. JavaScript: 21 test superati.
- Verificati credito configurato 4321.25, rollback, unicità del bonus, privacy,
  paginazione, divieto di modifica via API e rendering del ledger iniziale.
- Servizi locali riavviati e V15 applicata alla demo persistente; email disabilitate.
- Nel browser via IP registrato l'account di collaudo `collaudo_wallet_20261020`:
  10.000 CRD disponibili, riserva zero, un solo movimento Welcome credits,
  nessun modulo di modifica. Account lasciato nella demo, senza acquisti/offerte.

Requisiti, contratto REST, flussi UI, README, istruzioni AGENTS e collection
Postman aggiornati. La richiesta PUT in Postman rimane solo come test negativo
che deve ottenere 403. Nessun commit.

## Limite già presente

Questa modifica non azzera gli account esistenti e non risolve automaticamente
l'asta 6 bloccata in chiusura dall'offerta 9999999999.99 CRD: il relativo incasso
supera il limite del conto ADMIN. Saldi, riserva, offerta e retry sono conservati.
Un recupero della vecchia demo o una nuova regola di capienza dell'incasso
richiedono una decisione separata: non si alterano ledger o offerte storiche.
