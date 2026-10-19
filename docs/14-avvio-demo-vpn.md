# Avvio locale, dati demo e correzioni LAN/VPN — 19 ottobre 2026

## Risultato

Avviato LiveAuction con PostgreSQL, Producer e Consumer in Docker. Inseriti dati
di prova e configurato l'accesso HTTP tramite IP di rete. Corretti tre blocchi
distinti: cookie di sessione HTTPS su HTTP, collegamento WebSocket limitato a
localhost e generazione dell'UUID dei rilanci non disponibile su HTTP tramite IP.

La verifica finale dal browser ha inviato un'offerta di **1.112,00 CRD** con
`alice.demo` nell'asta demo della tastiera: conferma del Producer, nuovo leader,
prezzo, riserva del portafoglio ed estensione di 20 secondi visibili nella UI.

## Avvio del progetto

- Controllati README, istruzioni AGENTS, configurazioni, porte e strumenti locali.
- Il comando Java dell'host usava Java 8; utilizzate le immagini Docker del
  progetto, basate su Temurin Java 21, senza cambiare il Java dell'host.
- Compilate le immagini Producer e Consumer e avviato lo stack con
  `docker compose --profile prod up --build -d`.
- Generata inizialmente una chiave JWT locale in memoria. La stessa chiave è
  stata successivamente conservata nel `.env` locale ignorato da Git, insieme
  alle impostazioni della demo, per mantenere la configurazione nei riavvii.
- PostgreSQL usa il volume persistente `liveauction-postgres-data`.
- Verificati i tre container healthy, readiness Producer su `/api/v1/ready`,
  health Consumer su `/actuator/health` e pagina web su porta 8082.
- Nessun volume dati cancellato e nessuna migrazione Flyway modificata.

Servizi: Producer REST/STOMP su TCP 8081, Consumer web su TCP 8082 e PostgreSQL
locale su TCP 5432. I browser utilizzano solo 8081 e 8082.

## Dati di prova inseriti nel database locale

Gli inserimenti applicativi sono passati dalle API esistenti. L'unica modifica
diretta ai dati di dominio è stata la promozione del nuovo `admin.demo` ad ADMIN,
secondo la procedura di sviluppo documentata nel README. L'account già presente
prima del caricamento demo non è stato modificato dal caricamento.

### Account, portafogli e acquisti

| Account creato | Ruolo | Preparazione iniziale |
| --- | --- | --- |
| `admin.demo` | ADMIN | Portafoglio creato dalla registrazione; destinatario degli acquisti fissi |
| `alice.demo` | USER | Saldo virtuale impostato a 5.000,00 CRD tramite API |
| `bob.demo` | USER | Saldo virtuale impostato a 5.000,00 CRD tramite API |

Le credenziali demo sono state comunicate nella chat di lavoro. Password,
token e chiave JWT non sono riportati in questo documento.

Configurato localmente `SHOP_ADMIN_ID` con l'ID del nuovo ADMIN. Eseguiti due
acquisti tramite API, ciascuno con UUID idempotente distinto:

- `alice.demo`: un mouse wireless da 29,90 CRD; saldo dopo l'acquisto 4.970,10 CRD.
- `bob.demo`: due zaini da 39,90 CRD; saldo dopo l'acquisto 4.920,20 CRD.

Verificati inventari, movimenti `IMPOSTAZIONE_SALDO`, `ACQUISTO_FISSO` e
`INCASSO_FISSO`, incasso ADMIN di 109,70 CRD e coerenza dello stock riservato.
Questi saldi descrivono quel momento della demo: offerte e acquisti successivi
possono averli modificati.

### Catalogo

Create tre categorie: Informatica, Audio e gaming, Casa e tempo libero.
Quantità e prezzi della tabella sono quelli inseriti inizialmente, prima di
programmare aste ed eseguire acquisti.

| SKU | Prodotto | Prezzo fisso CRD | Astabile | Quantità iniziale |
| --- | --- | ---: | :---: | ---: |
| DEMO-LAPTOP | Notebook Studio 15 | 899,00 | sì | 6 |
| DEMO-TASTIERA | Tastiera meccanica RGB | 79,90 | sì | 15 |
| DEMO-MOUSE | Mouse wireless ergonomico | 29,90 | no | 20 |
| DEMO-MONITOR | Monitor 27 pollici QHD | 249,00 | sì | 8 |
| DEMO-CUFFIE | Cuffie Bluetooth | 89,90 | sì | 12 |
| DEMO-CONSOLE | Console portatile | 299,00 | sì | 5 |
| DEMO-SPEAKER | Speaker portatile | 49,90 | no | 0 |
| DEMO-ZAINO | Zaino urbano 25L | 39,90 | no | 18 |
| DEMO-CAFFE | Macchina caffe compatta | 119,00 | sì | 7 |
| DEMO-LAMPADA | Lampada da scrivania LED | 24,90 | no | 25 |

Lo speaker esaurito permette di verificare i controlli di disponibilità.

### Aste

Programmate tramite API cinque aste su cuffie, console, notebook, monitor e
macchina del caffè, con partenze distribuite fra pochi minuti e il giorno
successivo. Annullata tramite API l'asta della macchina del caffè per popolare
lo storico e verificare il rilascio dello stock.

Aggiunta poi l'asta demo **6**, sulla tastiera, per il controllo del collegamento
WebSocket e la prova finale del rilancio dalla UI. L'inizio era il
19 ottobre 2026 alle **12:10:14 Europe/Rome**, prezzo iniziale 10,00 CRD.
Il normale scheduler e le offerte determinano scadenza e stato successivi.

I dati restano nel database locale: questo intervento non aggiunge dump SQL,
seed automatici o dati demo alle migrazioni. I piccoli script di caricamento e
verifica utilizzati durante la sessione sono stati rimossi dopo l'esecuzione.

## Correzione della registrazione e del login tramite IP

Il profilo `prod` del Consumer imposta il cookie di sessione `Secure`. Su HTTP
non-localhost il browser non lo invia: il token CSRF del form non corrisponde
alla sessione e la POST viene respinta con **403**.

Riprodotto il 403 tramite l'IP di rete. Aggiunta in `compose.yaml` la variabile:

```yaml
SERVER_SERVLET_SESSION_COOKIE_SECURE: ${CONSUMER_SESSION_COOKIE_SECURE:-true}
```

Nel solo `.env` locale di prova impostato `CONSUMER_SESSION_COOKIE_SECURE=false`.
Il valore predefinito resta `true`, da mantenere quando si usa HTTPS.
Ricreato il Consumer e verificati via IP il passaggio della registrazione fino
al controllo dei duplicati sul Producer e il login fino alla pagina account.
La verifica del modulo non ha creato nuovi account oltre a quelli demo.

## Correzione del collegamento WebSocket remoto

Erano presenti due vincoli a localhost:

1. Il Consumer forniva al browser `ws://localhost:8081/ws`; su un PC remoto quel
   nome indica il PC remoto, non il server LiveAuction.
2. Il Producer ammetteva solo l'origine `http://localhost:8082`.

Modifiche applicate:

- `WebSocketConfig` riceve l'elenco delle origini dalla proprietà
  `app.websocket.allowed-origins` e usa `setAllowedOrigins` con origini esatte.
- La proprietà è configurabile tramite `LIVEAUCTION_WS_ALLOWED_ORIGINS`, con
  default `http://localhost:8082`.
- Docker Compose inoltra al Producer `LIVEAUCTION_WS_ALLOWED_ORIGINS` e al
  Consumer `LIVEAUCTION_PRODUCER_WS_URL`.
- Il `.env` locale usa l'indirizzo del server rilevato durante la sessione:

```dotenv
CONSUMER_SESSION_COOKIE_SECURE=false
LIVEAUCTION_PRODUCER_WS_URL=ws://172.21.142.130:8081/ws
LIVEAUCTION_WS_ALLOWED_ORIGINS=http://localhost:8082,http://172.21.142.130:8082
```

L'IP è quello della rete del PC durante il collaudo; non è un valore universale
né la prova che qualsiasi VPN lo instradi. Su un'altra rete sostituirlo con
l'indirizzo del server effettivamente raggiungibile dai browser.

Ricompilato il Producer e ricreati i due servizi applicativi. Verificati
handshake, STOMP, join e risposta al canale offerte per le origini localhost e
IP di rete. Verificato anche il rifiuto di un'origine non autorizzata con ticket
valido. I comandi non validi della prima verifica non hanno generato offerte.
Ticket monouso, sessioni, ruoli e regole economiche continuano a essere verificati.

## Correzione finale dei rilanci su HTTP tramite IP

Il controllo del trasporto non bastava a riprodurre l'intero comportamento del
browser. Nella UI la stanza mostrava `Live`, ma al click su `Place bid` la console
riportava:

```text
TypeError: crypto.randomUUID is not a function
```

`crypto.randomUUID()` richiede un contesto sicuro: funziona su HTTPS e sul
localhost considerato attendibile dal browser, ma non sul normale HTTP tramite
IP di rete. L'eccezione interrompeva il JavaScript prima di inviare il rilancio.

In `consumer/src/main/resources/static/js/auction-room.mjs` aggiunta una funzione
che usa `crypto.randomUUID()` quando disponibile e altrimenti genera un UUID v4
con `crypto.getRandomValues()`, impostando versione e variante. Il generatore
non usa `Math.random`. L'UUID del comando continua a essere conservato nei retry.

Aggiunto un test che simula l'assenza di `randomUUID`, invia due comandi distinti
e verifica UUID v4 diversi, importo e sequence. Il test è stato eseguito prima
della correzione, riproducendo lo stesso TypeError, e poi è passato con il fix.

Ricompilato e riavviato il Consumer. Da una scheda browser aperta su
`http://172.21.142.130:8082`, effettuati login e rilancio da 1.112,00 CRD con
`alice.demo` nell'asta 6. La UI ha mostrato `Bid confirmed by Producer.`, nuovo
prezzo e leader, due offerte, riserva di 1.112,00 CRD, disponibile di 3.858,10 CRD
ed estensione di 20 secondi. Salvata localmente la schermata in
`logs/rilancio-http-confermato.png`, cartella ignorata da Git. Chiusa la sessione
browser usata per il test.

## Verifiche automatiche eseguite

| Ambito | Risultato |
| --- | --- |
| Producer: autorizzazione STOMP, controller offerte, relay e registro presenza | 13 test superati |
| Producer: integrazione presenza con origine localhost e origine remota configurata | 2 test superati |
| Producer: integrazione offerte WebSocket | 8 test superati |
| Consumer: lobby e stanza JavaScript, inclusa la nuova regressione HTTP/IP | 13 test superati |
| Compilazione immagini Docker Producer e Consumer | riuscita con Java 21 |
| `git diff --check` | nessun errore |

I **23 test Producer effettivamente eseguiti** sono stati distribuiti in due
invocazioni. Nella prima, gli 8 test offerte erano saltati perché mancava
`RUN_DB_TESTS=true`; sono stati poi eseguiti separatamente con entrambe le
variabili `RUN_WS_TESTS=true` e `RUN_DB_TESTS=true`, tutti superati.

I test d'integrazione hanno usato il database temporaneo separato
`liveauction_vpn_test_20261019_6f87f8`, creato per questa verifica e rimosso dopo
il completamento. Le prove SMTP della suite usano il server simulato della suite,
senza inviare email reali. Il database della demo non è stato usato dalla suite.

Comando JavaScript dalla radice:

```powershell
node --test consumer/src/test/js/auction-lobby.test.cjs consumer/src/test/js/auction-room.test.mjs
```

Per ripetere i test Producer usare Java 21 e un database PostgreSQL dedicato,
impostando `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `RUN_WS_TESTS=true` e
`RUN_DB_TESTS=true`, poi:

```powershell
./producer/mvnw.cmd -f producer/pom.xml "-Dtest=AstaEventRelayTests,AstaOffertaControllerTests,AstaStompAuthorizationInterceptorTests,PresenceRegistryTests,PresenceWebSocketIntegrationTests,OfferteWebSocketIntegrationTests" test
```

Non è stata rieseguita in questa sessione tutta la suite Java Producer/Consumer;
i conteggi delle verifiche complete del 14 ottobre nel README restano storici.

## Istruzioni per gli altri PC e limiti della verifica

1. Il PC server deve rimanere acceso con Docker e i tre servizi avviati.
2. I client devono poter raggiungere il suo IP dalla LAN/VPN.
3. Sul server consentire in ingresso TCP **8082** per le pagine e TCP **8081**
   per il WebSocket, limitando la regola ai PC/reti di prova. Le sole regole in
   uscita non sostituiscono quelle in ingresso.
4. Aprire `http://IP_DEL_SERVER:8082`, aggiornare con Ctrl+F5 dopo il deploy ed
   effettuare nuovamente login dopo un riavvio del Consumer.
5. Entrare nella stessa asta mentre è APERTA. Ogni partecipante usa un account
   USER distinto, con saldo disponibile e importo almeno pari al minimo indicato.
   Il leader non può rilanciare su sé stesso.

Sono state lette le regole firewall in ingresso esistenti; non sono state create
o modificate regole firewall dall'agente. L'apertura delle porte è stata
effettuata dall'utente. Il collaudo browser descritto usa l'IP del server dal
browser sullo stesso PC: dimostra la correzione del problema HTTP/IP, ma non
certifica da solo l'instradamento o il firewall di ogni altro PC della VPN.
La matrice manuale completa con due browser su PC distinti resta da completare.

## File e pubblicazione

Aggiornati codice Consumer/Producer, test, `compose.yaml`, `.env.example`, README,
contratto WebSocket, flussi UI e questo resoconto. I payload delle API e delle
collection Postman non sono cambiati; non sono state introdotte nuove assegnazioni
del team.

Il `.env` effettivo, i dati PostgreSQL, le credenziali demo e la schermata locale
non fanno parte del commit. Il repository contiene configurazione di esempio,
codice, test e documentazione per ripetere l'avvio e le verifiche.
