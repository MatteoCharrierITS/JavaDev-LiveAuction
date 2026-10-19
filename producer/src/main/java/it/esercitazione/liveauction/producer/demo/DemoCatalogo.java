package it.esercitazione.liveauction.producer.demo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Sorgente dei prodotti demo: DummyJSON (gratuita, senza chiave) con elenco statico incorporato
 * come ripiego. Non lancia mai eccezioni verso il chiamante: senza rete usa l'elenco statico.
 */
final class DemoCatalogo {

    private static final Logger log = LoggerFactory.getLogger(DemoCatalogo.class);

    /** Prezzo massimo in CRD: gli utenti demo partono da 5000 CRD. */
    static final int PREZZO_MASSIMO = 4000;

    private static final Map<String, String> NOMI_CATEGORIE = Map.of(
            "beauty", "Bellezza", "fragrances", "Profumi", "furniture", "Arredamento",
            "groceries", "Alimentari", "laptops", "Computer", "smartphones", "Smartphone");

    record DemoProdotto(String categoriaSlug, String categoriaNome, String sku, String nome,
                        String descrizione, String immagineUrl, BigDecimal prezzoCrd, int stock) {
    }

    private DemoCatalogo() {
    }

    static List<DemoProdotto> carica(String url, ObjectMapper mapper) {
        try {
            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(4))
                    .followRedirects(HttpClient.Redirect.NORMAL).build();
            HttpResponse<String> risposta = client.send(HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(8)).header("Accept", "application/json").GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            if (risposta.statusCode() != 200) {
                throw new IOException("HTTP " + risposta.statusCode());
            }
            List<DemoProdotto> prodotti = interpreta(risposta.body(), mapper);
            if (prodotti.isEmpty()) {
                throw new IOException("nessun prodotto valido nella risposta");
            }
            return prodotti;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            log.warn("Catalogo demo: caricamento interrotto, uso l'elenco statico");
        } catch (Exception exception) {
            log.warn("Catalogo demo: {} non raggiungibile o non valido ({}), uso l'elenco statico",
                    url, exception.toString());
        }
        return statico();
    }

    /** Formato DummyJSON: {"products":[{title,description,category,price,sku,thumbnail,stock}]}. */
    static List<DemoProdotto> interpreta(String json, ObjectMapper mapper) throws IOException {
        List<DemoProdotto> risultato = new ArrayList<>();
        for (JsonNode nodo : mapper.readTree(json).path("products")) {
            String titolo = nodo.path("title").asText("").trim();
            String categoria = slug(nodo.path("category").asText(""));
            String sku = nodo.path("sku").asText("").trim();
            if (titolo.isEmpty() || categoria.isEmpty() || sku.isEmpty() || !nodo.path("price").isNumber()) {
                continue;
            }
            String thumbnail = nodo.path("thumbnail").asText("").trim();
            risultato.add(new DemoProdotto(categoria, nomeCategoria(categoria),
                    tronca(sku, 30).toUpperCase(Locale.ROOT), tronca(titolo, 200),
                    tronca(nodo.path("description").asText("").trim(), 5000),
                    thumbnail.matches("https?://\\S{1,480}") ? thumbnail : null,
                    inCrd(nodo.path("price").decimalValue()), Math.max(0, nodo.path("stock").asInt(0))));
        }
        return risultato;
    }

    /** Prezzo in dollari della sorgente convertito in crediti interi (x5), tra 5 e {@link #PREZZO_MASSIMO}. */
    static BigDecimal inCrd(BigDecimal prezzo) {
        BigDecimal crd = prezzo.multiply(BigDecimal.valueOf(5)).setScale(0, RoundingMode.HALF_UP);
        return crd.max(BigDecimal.valueOf(5)).min(BigDecimal.valueOf(PREZZO_MASSIMO)).setScale(2);
    }

    private static String slug(String categoria) {
        return categoria.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
    }

    private static String nomeCategoria(String slug) {
        String nome = NOMI_CATEGORIE.get(slug);
        if (nome != null) {
            return nome;
        }
        String testo = slug.replace('-', ' ');
        return Character.toUpperCase(testo.charAt(0)) + testo.substring(1);
    }

    private static String tronca(String testo, int massimo) {
        return testo.length() <= massimo ? testo : testo.substring(0, massimo);
    }

    private static DemoProdotto fisso(String categoria, String sku, String nome, String descrizione,
                                      int prezzo, int stock) {
        return new DemoProdotto(categoria, nomeCategoria(categoria), sku, nome, descrizione,
                "https://picsum.photos/seed/" + sku.toLowerCase(Locale.ROOT) + "/400/400",
                BigDecimal.valueOf(prezzo).setScale(2), stock);
    }

    static List<DemoProdotto> statico() {
        return List.of(
                fisso("furniture", "DEMO-FUR-001", "Poltrona di velluto", "Poltrona imbottita con gambe in legno.", 1200, 5),
                fisso("furniture", "DEMO-FUR-002", "Scrivania in rovere", "Scrivania con due cassetti, piano in rovere massello.", 1800, 4),
                fisso("furniture", "DEMO-FUR-003", "Lampada da terra", "Lampada ad arco con base in marmo.", 450, 12),
                fisso("fragrances", "DEMO-FRA-001", "Eau de parfum Notte", "Fragranza legnosa e speziata, 50 ml.", 400, 6),
                fisso("fragrances", "DEMO-FRA-002", "Colonia Agrumi", "Colonia fresca di bergamotto e limone, 100 ml.", 150, 20),
                fisso("fragrances", "DEMO-FRA-003", "Profumo Ambra", "Profumo orientale all'ambra, 30 ml.", 300, 8),
                fisso("beauty", "DEMO-BEA-001", "Palette ombretti", "Dodici tonalita' con specchio integrato.", 90, 25),
                fisso("beauty", "DEMO-BEA-002", "Rossetto rosso classico", "Formula cremosa a lunga tenuta.", 60, 40),
                fisso("beauty", "DEMO-BEA-003", "Crema viso idratante", "Crema giorno per tutti i tipi di pelle.", 110, 30),
                fisso("groceries", "DEMO-GRO-001", "Cesto di frutta di stagione", "Frutta fresca selezionata, circa 3 kg.", 40, 30),
                fisso("groceries", "DEMO-GRO-002", "Olio extravergine", "Bottiglia da 750 ml, prima spremitura a freddo.", 55, 40),
                fisso("groceries", "DEMO-GRO-003", "Miele di castagno", "Vasetto da 500 g, produzione locale.", 35, 50));
    }
}
