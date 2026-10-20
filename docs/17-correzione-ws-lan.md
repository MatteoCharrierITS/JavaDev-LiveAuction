# Offerte bloccate dopo cambio rete — 20 ottobre 2026

## Causa e riscontro

L'asta 6 era realmente APERTA e lo snapshot REST funzionava, ma la stanza
rimaneva in Connecting/Reconnecting e disabilitava le offerte. Il Mac era
tornato su `10.135.188.192`, mentre il Consumer pubblicava ancora il WebSocket
`ws://192.168.137.99:8081/ws`; il Producer ammetteva solo l'origine del vecchio
IP oltre a localhost/loopback. Il mancato collegamento live era quindi un
disallineamento di rete/configurazione, non un rifiuto delle regole economiche.

## Correzione

- Modalità `LIVEAUCTION_PRODUCER_WS_URL=auto`, ora default anche in Compose e
  `.env.example`: host della pagina Consumer, porta 8081, percorso `/ws`.
  Rimane possibile un override completo per porte, host o proxy differenti.
- Aggiornate nel `.env` locale le origini esatte delle due reti note, mantenendo
  localhost e loopback. Nessuna wildcard, modifica a ticket/ruoli o bypass.
- Script della stanza versionato per evitare che il browser conservi la vecchia
  risoluzione dell'endpoint. Riavviati i servizi, senza cancellare dati.
- API REST e payload STOMP invariati; Postman non richiede nuove richieste.

Il controllo delle origini non è automatico: una nuova rete con un indirizzo
diverso richiede ancora l'aggiornamento delle origini e il riavvio del Producer.
Una prenotazione DHCP o un hostname stabile evita continui cambi di indirizzo.
La modalità `auto` non aggiunge HTTPS/TLS al Producer.

## Prove

Test di regressione per localhost, LAN, cambio IP, IPv6, HTTPS, override,
ticket e offerte disabilitate prima della connessione. Superati 63 test Java
Consumer e 21 JavaScript. Nel browser via IP confermato un rilancio di 10 CRD
sull'asta demo 6, con riserva fondi ed estensione di 20 secondi.
Sulla versione finale con endpoint automatico, una seconda sessione indipendente
(`utente2`, accesso via loopback) ha rilanciato a 11 CRD: conferma privata,
aggiornamento live di prezzo/leader/conteggio nell'altra sessione via IP e
rilascio della precedente riserva di `utente1`, senza ricaricare la pagina.
Presenza pari a due utenti distinti. Non sono due PC fisici.
Le prove usano crediti virtuali reali della demo e non azzerano il ledger.
Il collaudo da un secondo PC fisico va ripetuto dagli utenti sulla rete effettiva.

Indirizzo attivo al controllo: `http://10.135.188.192:8082`.
Ricaricare la stanza e rifare il login dopo il riavvio del Consumer.
Email ancora disabilitate; nessun commit.
