# Demo persistente multi-PC — 20 ottobre 2026

Base `main` `2a5c487`, modifiche locali senza commit. Il resoconto del 19 ottobre
è storico: questo documento descrive l'ambiente attuale.

## Accesso

- UI: `http://10.135.188.192:8082`; REST/WebSocket sullo stesso IP, porta 8081.
- `maikol`: ADMIN, ID 1 e destinatario degli acquisti (`SHOP_ADMIN_ID=1`).
- `utente1` e `utente2`: USER, 5.000 CRD iniziali ciascuno.
- Password casuali comunicate nella chat, mai nei file versionati.
- Email **disabilitate** (`EMAIL_ENABLED=false`), nessun SMTP esterno.

Gli altri PC devono essere sulla stessa rete e raggiungere TCP 8081 e 8082.
Reti ospiti, isolamento Wi-Fi e VPN possono impedire l'accesso. Non sono state
modificate regole firewall né aperte porte sul router. La configurazione è per
una rete fidata: HTTP non cifra password/sessioni; non esporla a Internet.
Se cambia l'IP del Mac, aggiornare URL WebSocket e origini nel `.env` e riavviare.

## Database e dati

Nuovo database **`liveauction_demo_20261020`** nel container PostgreSQL esistente
`liveauction-postgres`, porta 5433, volume persistente `liveauction-postgres-data`.
Il precedente **`liveauction` resta conservato** e non è usato dalla demo.
Nessun reset o modifica correttiva delle vecchie righe.

Audit in sola lettura del vecchio DB: otto aste APERTA scadute, senza offerte
né movimenti di settlement; ciascun prodotto ha bloccato zero e disponibile
uno. Non sono stati inventati stock o forzate chiusure. La riparazione rimane
separata dalla preparazione del nuovo ambiente.

Creati tre account, tre categorie e otto prodotti, inclusi non astabili ed
esauriti. Tutti i dati applicativi sono stati caricati tramite API; unica
scrittura SQL di dominio, la promozione esplicita di maikol ad ADMIN prevista
dal README. Nessun seed nelle migrazioni.

Le aste del 20 ottobre, orario Europe/Rome:

| ID | Prodotto | Inizio | Prezzo iniziale CRD |
| --- | --- | --- | ---: |
| 1 | Tastiera meccanica | 12:15:54 | 10,00 |
| 2 | Notebook Studio | 12:25:34 | 100,00 |
| 3 | Cuffie Bluetooth | 12:35:34 | 15,00 |
| 4 | Monitor 27 pollici | 12:55:34 | 25,00 |

L'asta 5 è stata programmata dalla UI e annullata senza offerte, verificando
il rilascio dello stock. Altre aste si creano da **ADMIN catalog → Schedule**.
Durata iniziale sette minuti; ogni offerta aggiunge venti secondi.

Sono già presenti movimenti delle prove: offerte 10 CRD di utente1 e 11 CRD
di utente2 sull'asta 1; acquisto di un mouse da utente1 a 29,90 CRD, con incasso
ADMIN e inventario aggiornato. I saldi seguono i movimenti e non restano
necessariamente quelli iniziali.

## Configurazione e riavvio

Producer e Consumer sono JAR Java 21 sull'host; PostgreSQL è in Docker. Il
`.env` locale ignorato da Git, con permessi 600, conserva database demo, chiave
JWT casuale, ADMIN destinatario, email false, bind di rete e origini esatte
localhost/127.0.0.1/IP. Per HTTP LAN i cookie non sono Secure, ma conservano
HttpOnly, SameSite e CSRF. Non usare questa configurazione per dati reali.

Con Docker Desktop avviato, riavviare il database esistente:

```sh
docker start liveauction-postgres
```

Da due terminali nella radice del progetto, caricare l'ambiente e avviare con
il comando `java` del **JDK 21** rispettivamente Producer e Consumer:

```sh
set -a
. ./.env
set +a
# Primo terminale:
java -jar producer/target/liveauction-producer-0.0.1-SNAPSHOT.jar
# Secondo terminale, dopo lo stesso caricamento del .env:
java -jar consumer/target/liveauction-consumer-0.0.1-SNAPSHOT.jar
```

Non avviare contemporaneamente container applicativi Compose sulle stesse porte
dei JAR. Database e account persistono nel volume; non ricrearli a ogni riavvio.

## Scheduler e verifiche

Gli errori di lettura, transizione e chiusura vengono segnalati subito con
stack trace; per la stessa operazione/asta lo stack viene ripetuto al massimo
ogni sessanta secondi, contando tutti i tentativi falliti. Il successo registra
il recupero e azzera il contatore. **I retry restano a ogni ciclo**, senza
modificare stock, saldi o transazioni. Nessuna nuova pagina diagnostica: le
aste bloccate sono segnalate nei log.

- Build con Temurin 21.0.11 riuscita.
- **297 test Producer e 52 Consumer**, zero errori/fallimenti/saltati, database
  PostgreSQL `liveauction_verifica_20261020` separato dalla demo, WebSocket reali.
- **13 test JavaScript** superati. Tre nuovi test coprono la limitazione dei
  log, aste distinte e recupero; i nove test scheduler continuano a verificare
  i retry dopo errori.
- Suite eseguita con pool Hikari maximum-pool-size=4 e minimum-idle=0: il primo
  tentativo con pool predefiniti superava il limite di connessioni insieme al
  servizio attivo. Nessun aumento del limite PostgreSQL.
- Due sessioni web simultanee, cookie separati fra hostname localhost e IP:
  login USER, rilanci, presenza, aggiornamento live e rilascio della riserva.
- Login ADMIN, pannello, acquisto via IP, inventario, programmazione e
  annullamento dalla UI verificati. Incasso ADMIN di 29,90 CRD controllato nel DB.
- Readiness Producer e health Consumer UP; Flyway V14 sulla demo.

Verificata anche la chiusura naturale dell'asta 1 alle 12:23:34, dopo sette
minuti più due estensioni da venti secondi: utente2 vincitore a 11 CRD,
inventario con una tastiera, riserva azzerata, un solo pagamento e un solo
incasso nel ledger. Stock bloccato zero, disponibilità 19. Incasso totale
ADMIN 40,90 CRD (29,90 acquisto + 11 asta), saldo utente1 4.970,10 e utente2
4.989,00. La notifica resta in coda PENDING con zero tentativi, coerentemente
con l'invio email disabilitato. Annuncio del vincitore e inventario verificati
nella UI, senza anticipare tempi in SQL.

Le due sessioni sono nello stesso browser sul Mac, non su due PC fisici.
Raggiungibilità dai computer esterni e matrice completa delle interruzioni di
rete restano da provare sulla rete effettiva. Le chiusure/rollback/concorrenza
sono coperte dalla suite. Non sono stati anticipati in SQL gli orari della demo.
