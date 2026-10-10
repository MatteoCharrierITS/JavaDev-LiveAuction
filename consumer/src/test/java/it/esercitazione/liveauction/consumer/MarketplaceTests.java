package it.esercitazione.liveauction.consumer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.RestClient;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@SpringBootTest
@AutoConfigureMockMvc
class MarketplaceTests {
    @TestConfiguration
    static class StubConfig {
        @Bean StubHolder marketplaceStub() { return new StubHolder(); }
        @Bean @Primary RestClient marketplaceRestClient(StubHolder stub) { return stub.builder.build(); }
    }

    static class StubHolder {
        final RestClient.Builder builder = RestClient.builder().baseUrl("http://producer.test/api/v1");
        final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    }

    @Autowired MockMvc mvc;
    @Autowired StubHolder stub;

    @BeforeEach void reset() { stub.server.reset(); }

    private void expect(String path, HttpStatus status, String body, MediaType mediaType) {
        stub.server.expect(requestTo("http://producer.test/api/v1" + path))
                .andRespond(withStatus(status).contentType(mediaType).body(body));
    }

    private void categories() {
        expect("/categorie", HttpStatus.OK,
                "[{\"id\":4,\"nome\":\"Office\",\"slug\":\"office\",\"attiva\":true}]", MediaType.APPLICATION_JSON);
    }

    private String product(String price, int available) {
        return "{\"id\":7,\"nome\":\"Desk lamp\",\"descrizione\":\"Useful light\","
                + "\"prezzoFisso\":" + price + ",\"astabile\":true,\"quantitaDisponibile\":" + available
                + ",\"quantitaBloccata\":2,\"asteProgrammate\":1,"
                + "\"categoria\":{\"id\":4,\"nome\":\"Office\",\"slug\":\"office\",\"attiva\":true}}";
    }

    @Test void filtersAndPaginationPreserveSelection() throws Exception {
        categories();
        expect("/prodotti?query=desk&categoria=office&astabile=true&page=1&size=2", HttpStatus.OK,
                "{\"content\":[" + product("49.90", 3) + "],\"page\":1,\"size\":2,\"totalElements\":5,\"totalPages\":3}",
                MediaType.APPLICATION_JSON);
        mvc.perform(get("/marketplace").param("query", "desk").param("categoria", "office")
                        .param("astabile", "true").param("page", "1").param("size", "2"))
                .andExpect(status().isOk()).andExpect(view().name("marketplace/list"))
                .andExpect(content().string(containsString("Desk lamp")))
                .andExpect(content().string(containsString("49.90")))
                .andExpect(content().string(containsString("AVAILABLE FOR AUCTION")))
                .andExpect(content().string(containsString("query=desk")))
                .andExpect(content().string(containsString("categoria=office")))
                .andExpect(content().string(containsString("astabile=true")))
                .andExpect(content().string(containsString(
                        "query=desk&amp;categoria=office&amp;astabile=true&amp;page=2&amp;size=2")));
        stub.server.verify();
    }

    @Test void emptyResultAndInvalidPagination() throws Exception {
        categories();
        expect("/prodotti?page=0&size=12", HttpStatus.OK,
                "{\"content\":[],\"page\":0,\"size\":12,\"totalElements\":0,\"totalPages\":0}", MediaType.APPLICATION_JSON);
        mvc.perform(get("/marketplace")).andExpect(status().isOk())
                .andExpect(content().string(containsString("No products match these filters.")))
                .andExpect(content().string(not(containsString("class=\"product-card\""))));
        mvc.perform(get("/marketplace").param("size", "101")).andExpect(status().isBadRequest());
        stub.server.verify();
    }

    @Test void detailShowsOptionalPriceAndStockWithoutPurchaseAction() throws Exception {
        expect("/prodotti/7", HttpStatus.OK, product("null", 0), MediaType.APPLICATION_JSON);
        mvc.perform(get("/prodotti/7")).andExpect(status().isOk())
                .andExpect(view().name("marketplace/detail"))
                .andExpect(content().string(containsString("No fixed price")))
                .andExpect(content().string(containsString("Currently unavailable")))
                .andExpect(content().string(containsString("Blocked quantity")))
                .andExpect(content().string(not(containsString("Buy now"))));
        stub.server.verify();
    }

    @Test void hiddenProductIs404AndProducerFailureIsVisible() throws Exception {
        expect("/prodotti/7", HttpStatus.NOT_FOUND,
                "{\"status\":404,\"code\":\"RISORSA_NON_TROVATA\"}", MediaType.APPLICATION_PROBLEM_JSON);
        mvc.perform(get("/prodotti/7")).andExpect(status().isNotFound())
                .andExpect(view().name("marketplace/error"))
                .andExpect(content().string(containsString("Product not found")));
        stub.server.verify();

        stub.server.reset();
        categories();
        expect("/prodotti?page=0&size=12", HttpStatus.SERVICE_UNAVAILABLE, "{}", MediaType.APPLICATION_PROBLEM_JSON);
        mvc.perform(get("/marketplace")).andExpect(status().isServiceUnavailable())
                .andExpect(content().string(containsString("temporarily unavailable")));
        stub.server.verify();
    }
}
