# Dati demo all'avvio del Producer

Il Producer può popolare un database vuoto con utenti, catalogo e aste di prova.
È disattivato per default (`app.demo.seed=false`); si attiva con `DEMO_SEED=true`
(il `compose.yaml` lo imposta a `true` se la variabile non è definita).
Classi: `producer/src/main/java/.../producer/demo/` (`DemoSeeder`, `DemoCatalogo`).

## Cosa crea (solo ciò che manca)

- **Utenti** (password comune `Demo2026!`, modificabile con `DEMO_PASSWORD` solo alla
  prima creazione): `matteo` (ADMIN), `andrea`, `maikol`, `cristian`, `marco`, `tommi`
  (USER), email `<username>@demo.local`. Passano da `RegistrazioneService`; ogni USER
  riceve 5000 CRD con un movimento di ledger `IMPOSTAZIONE_SALDO`, una sola volta
  (solo se il portafoglio non ha movimenti). L'ADMIN ha il portafoglio per incassare.
- **Categorie e prodotti** da DummyJSON
  (`https://dummyjson.com/products?limit=40&select=title,description,category,price,sku,thumbnail,stock`,
  senza chiave; URL sostituibile con `app.demo.catalog-url`). Con la rete assente o la
  risposta non valida viene usato un elenco statico incorporato (SKU `DEMO-*`, immagini
  `picsum.photos`): l'avvio non fallisce mai per questo. I prezzi in dollari diventano
  CRD interi (x5, tra 5 e 4000); arredamento, profumi e parte degli altri prodotti sono
  astabili, con stock positivo. `immagineUrl` è la miniatura della sorgente. Creazione
  per SKU: gli SKU già presenti non vengono toccati.
- **Aste**: 4 aste (una per categoria, finché possibile) che partono tra 1-2 minuti, 45
  minuti, 2 ore e 4 ore, con base pari al 15% del prezzo fisso (10-600 CRD). Vengono
  create solo se non esistono aste `PROGRAMMATA`, `STANZA_APERTA` o `APERTA`.
- **Acquisti fissi**: se `SHOP_ADMIN_ID` è 0, il seeder lo imposta in memoria all'id
  dell'ADMIN `matteo` (o, se manca, del primo ADMIN attivo). Un valore diverso da 0 non
  viene toccato. Senza `DEMO_SEED` il comportamento resta quello descritto in
  [inventario e acquisti](13-inventario-acquisti.md).

Catalogo e aste usano `CategoriaService`, `ProdottoService` e `AstaService`, con un
contesto di sicurezza ADMIN creato dal seeder, quindi rispettano validazioni, stock e
invarianti. Gli errori del seeder vengono registrati nel log e non bloccano l'avvio.
