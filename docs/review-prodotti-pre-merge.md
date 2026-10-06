# Review modulo Prodotti — attività prima del merge

Il modulo Prodotti è tecnicamente ben strutturato e, nella verifica del 6 ottobre
2026, il merge simulato con `main` ha superato tutti i 19 test disponibili.
Prima del merge restano però alcune attività per allineare implementazione,
contratto API e strumenti di test.

## Da risolvere prima del merge

### 1. Aggiornare il contratto REST

`docs/04-api-rest.md` non documenta tutti gli endpoint implementati. Aggiungere
metodo, percorso, ruolo richiesto, DTO, risposta ed errori almeno per:

- `GET /api/v1/categorie`;
- `GET`, `POST` e `PUT /api/v1/admin/categorie/**`;
- `GET /api/v1/admin/prodotti` e `GET /api/v1/admin/prodotti/{id}`;
- `POST /api/v1/admin/prodotti`;
- `PUT /api/v1/admin/prodotti/{id}`.

Per la modifica prodotto va chiarito che il body è una sostituzione completa e
richiede anche `categoriaId`, `sku`, `nome`, `astabile`,
`quantitaDisponibile`, `attivo` e `versione`. `quantitaBloccata` deve restare
gestita esclusivamente dal dominio aste.

### 2. Aggiornare la collection Postman

`docs/postman/prodotti.json` dichiara ancora che catalogo e modifica prodotto
non sono implementati. Aggiornare descrizioni e richieste, aggiungere i nuovi
endpoint e sostituire il body provvisorio del `PUT` con un payload completo, ad
esempio:

```json
{
  "categoriaId": 1,
  "sku": "INF-LAP-001",
  "nome": "Laptop Pro 15",
  "descrizione": "Descrizione aggiornata",
  "prezzoFisso": 1299.90,
  "astabile": true,
  "quantitaDisponibile": 3,
  "attivo": true,
  "versione": 0
}
```

Aggiornare anche lo stato del modulo in `docs/postman/README.md`.

### 3. Chiarire lo scope dell'acquisto a prezzo fisso

Il contratto prevede `POST /api/v1/prodotti/{id}/acquisti`, ma il branch non lo
implementa. Se appartiene a un altro incarico, indicarlo nel piano di lavoro e
lasciarlo esplicitamente come API futura. Se il branch deve completare l'intero
modulo Prodotti, implementarlo con controllo stock, portafoglio, inventario e
movimento di ledger nella stessa transazione.

## Decisione di dominio richiesta

### Prodotti di una categoria disattivata

Attualmente `GET /api/v1/categorie` nasconde le categorie disattivate, mentre
il catalogo continua a mostrare i loro prodotti se `prodotti.attivo = true`.
Il team deve scegliere e documentare una delle due regole:

1. disattivare una categoria nasconde anche i suoi prodotti dal catalogo
   pubblico; in questo caso il filtro pubblico deve richiedere anche
   `categoria.attiva = true`;
2. la categoria disattivata impedisce solo nuovi utilizzi amministrativi, ma i
   prodotti restano visibili; in questo caso il comportamento attuale va
   dichiarato esplicitamente.

## Test consigliati

Ampliare `CatalogoIntegrationTests` con scenari separati per:

- modifica e disattivazione di una categoria;
- visibilità dei prodotti appartenenti a categorie disattivate;
- filtri e dettaglio ADMIN;
- conteggio `asteProgrammate` con aste realmente presenti nei diversi stati;
- validazione dei DTO e risposta `application/problem+json`;
- aggiornamento prodotto con `versione` obsoleta e, se possibile, concorrenza
  sullo stock.

## Verifica finale

Dopo gli aggiornamenti, riallineare il branch a `main` e lanciare una build
pulita. I test di integrazione richiedono PostgreSQL:

```bash
docker compose up -d postgres
RUN_DB_TESTS=true RUN_WS_TESTS=true ./producer/mvnw -f producer/pom.xml clean test
```

Risultato atteso: nessun test fallito o saltato e nessuna regressione nei moduli
autenticazione e WebSocket.
