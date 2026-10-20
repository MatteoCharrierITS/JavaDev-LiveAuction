# Database ufficiale e prova su hotspot — 22 ottobre 2026

Preparato il 21 ottobre. Il `.env` locale seleziona `liveauction_ufficiale_20261022`,
PostgreSQL 16 nel volume `liveauction-postgres-data`, porta host 5433.
Schema creato da Flyway V1–V15, senza dati dimostrativi nelle migrazioni.

## Account e catalogo

| Account | Ruolo | Crediti iniziali | Riservati |
| --- | --- | --- | --- |
| maikol | ADMIN | 10.000 CRD | 0 |
| utente1 | USER | 10.000 CRD | 0 |
| utente2 | USER | 10.000 CRD | 0 |
| utente3 | USER | 10.000 CRD | 0 |

I primi tre conservano ID, username, email e hash originali; verificato il login
con le stesse credenziali. Trasferiti soltanto i profili, non aste, offerte,
inventario o transazioni. Ogni wallet ha un solo movimento `CREDITO_INIZIALE`.
`SHOP_ADMIN_ID=1`: maikol riceve gli acquisti fissi. Wallet di sola lettura.

`utente3` creato dalla vera pagina di registrazione, poi autenticato. Password
consegnata in chat e conservata in `.local/credenziali-utente3.txt`, escluso da Git.
Non allegare alla consegna `.env`, file di credenziali o dump.

| Articolo | Prezzo CRD | Astabile | Quantità |
| --- | --- | --- | --- |
| Licenza Eclipse (edizione simbolica) | 25 | sì | 30 |
| Cartellone TOP | 35 | sì | 30 |
| Pupazzo Java — Duke | 150 | sì | 30 |
| Pupazzo del pitone maledetto (Python) | 120 | sì | 30 |
| Tastiera meccanica del programmatore | 250 | sì | 30 |
| Tazza da debug | 40 | no | 30 |

Due categorie attive, inserimento dalle API ADMIN, stock bloccato zero.
Licenza Eclipse simbolica, non licenza software reale. Illustrazioni SVG locali
non ufficiali; nessuna API esterna necessaria. `scripts/catalogo-consegna.json`
documenta i dati: risolvere il campo `categoria` (slug) in `categoriaId` prima
di chiamare l'API. Non è un import automatico e non viene rieseguito all'avvio.

## Avvio sulla rete del telefono

1. Collegare Mac e PC allo stesso hotspot **prima** di avviare i servizi.
2. Aprire Docker Desktop. Servono Java 21 e le dipendenze Maven già scaricate.
3. Dalla radice: `bash scripts/avvia-locale.sh`.
4. Lasciare il terminale aperto e aprire sui PC l'URL stampato:
   `http://IP_DEL_MAC:8082/marketplace`, non localhost sui PC remoti.
5. Arresto: Ctrl+C oppure `bash scripts/ferma-locale.sh`. Dati e volume conservati.

Lo script compila i JAR e avvia PostgreSQL, Producer e Consumer, aspettando la
readiness. Rileva l'IPv4 dell'interfaccia della route predefinita e configura
origini WebSocket esatte per quell'IP, localhost e loopback. Endpoint browser
`auto`, porta 8081. Nessuna wildcard. Dopo un cambio rete fermare e riavviare.
Con VPN o più schede indicare l'IP Wi-Fi del Mac esplicitamente:
`bash scripts/avvia-locale.sh --ip IP_DEL_MAC`.

HTTP solo sulla rete locale fidata: cookie Secure disattivato e servizi su tutte
le interfacce; ticket, CSRF, token e ruoli restano attivi. Email disabilitate
dallo script. Non esporre le porte su Internet. Consentire TCP 8081 e 8082 a Java
se richiesto dal firewall; nessuna impostazione del firewall è stata modificata.
Per evitare sospensione: `caffeinate -i bash scripts/avvia-locale.sh` sul Mac.

## Verifiche e attenzioni

298 test Producer superati, nessuno saltato, con PostgreSQL separato, WebSocket e
SMTP simulato; 67 Consumer e 21 JavaScript. Registrazione reale e wallet via UI,
login dei quattro account, catalogo e immagini verificati via IP della rete attuale.
API: registrazione duplicata 409, input invalido 400, modifica wallet 403.
Nessuna nuova regola di dominio, API o migrazione rispetto al commit di base.

Stato finale pulito: quattro account, sei prodotti, zero aste, offerte, acquisti
e inventario. Non sono stati effettuati rilanci o acquisti sul database ufficiale.
Il collaudo su un secondo PC del **vero hotspot** resta da effettuare: alcuni
hotspot isolano i dispositivi. Verificare pagina su 8082 e stanza Live su 8081.
Una stanza aperta tre minuti prima dell'inizio non permette offerte: attendere
APERTA. Usare account diversi in browser/finestre distinti.

La vecchia asta bloccata dall'offerta massima non è importata. Il limite di
capienza dell'incasso non è stato modificato: non è un bug dichiarato risolto.

## Pulizia e recuperabilità

Eliminati, su richiesta, i 18 database storici LiveAuction di sviluppo/test/review,
compresi `liveauction` e `liveauction_demo_20261020`. Dump verificati prima della
cancellazione in `.local/backups/`, con permessi riservati e ignorati da Git.
Nessun volume Docker o database di altri progetti è stato rimosso.

Backup del nuovo stato iniziale:
`.local/backups/liveauction_ufficiale_20261022-pulito.dump`.
Ripristino provato in un database temporaneo: 4 utenti, 6 prodotti, 40.000 CRD.
I database temporanei di collaudo sono stati eliminati dopo le verifiche.
Per recuperare usare `pg_restore` in un database nuovo, senza sovrascrivere
quello ufficiale dopo l'inizio delle prove.

Al termine servizi spenti, database persistito. Nessun commit eseguito.
