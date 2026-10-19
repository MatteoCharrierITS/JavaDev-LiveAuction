package it.esercitazione.liveauction.producer.demo;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.esercitazione.liveauction.producer.demo.DemoCatalogo.DemoProdotto;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DemoCatalogoTests {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void interpretaIlFormatoDummyJson() throws Exception {
        String json = """
                {"products":[
                  {"id":1,"title":"Essence Mascara","description":"Un mascara.","category":"beauty","price":9.99,
                   "sku":"bea-ess-ess-001","thumbnail":"https://cdn.dummyjson.com/x/thumbnail.webp","stock":99},
                  {"id":2,"title":"Senza prezzo","category":"beauty","sku":"X"},
                  {"id":3,"title":"Divano","description":"d","category":"Home Decoration","price":2499.99,
                   "sku":"FUR-1","thumbnail":"javascript:alert(1)","stock":5}
                ],"total":194,"skip":0,"limit":3}""";
        List<DemoProdotto> prodotti = DemoCatalogo.interpreta(json, MAPPER);

        assertThat(prodotti).hasSize(2);
        DemoProdotto primo = prodotti.get(0);
        assertThat(primo.sku()).isEqualTo("BEA-ESS-ESS-001");
        assertThat(primo.categoriaSlug()).isEqualTo("beauty");
        assertThat(primo.categoriaNome()).isEqualTo("Bellezza");
        assertThat(primo.prezzoCrd()).isEqualByComparingTo("50");
        assertThat(primo.immagineUrl()).startsWith("https://");
        DemoProdotto secondo = prodotti.get(1);
        assertThat(secondo.categoriaSlug()).isEqualTo("home-decoration");
        assertThat(secondo.categoriaNome()).isEqualTo("Home decoration");
        assertThat(secondo.prezzoCrd()).isEqualByComparingTo(BigDecimal.valueOf(DemoCatalogo.PREZZO_MASSIMO));
        assertThat(secondo.immagineUrl()).isNull();
    }

    @Test
    void senzaReteUsaLElencoStaticoSenzaLanciareEccezioni() {
        List<DemoProdotto> prodotti = DemoCatalogo.carica("http://127.0.0.1:1/non-raggiungibile", MAPPER);

        assertThat(prodotti).isEqualTo(DemoCatalogo.statico());
        assertThat(prodotti).allSatisfy(p -> {
            assertThat(p.immagineUrl()).startsWith("https://");
            assertThat(p.sku()).hasSizeLessThanOrEqualTo(30);
            assertThat(p.stock()).isPositive();
            assertThat(p.prezzoCrd()).isBetween(BigDecimal.valueOf(5), BigDecimal.valueOf(DemoCatalogo.PREZZO_MASSIMO));
        });
        assertThat(prodotti.stream().map(DemoProdotto::sku).distinct()).hasSameSizeAs(prodotti);
    }
}
