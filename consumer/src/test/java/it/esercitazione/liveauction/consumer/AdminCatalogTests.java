package it.esercitazione.liveauction.consumer;

import it.esercitazione.liveauction.consumer.auth.AuthSessionService;
import it.esercitazione.liveauction.consumer.auth.SessionAuth;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.RestClient;

import java.time.Instant;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class AdminCatalogTests {
    @TestConfiguration
    static class StubConfig {
        @Bean AdminStub adminStub() { return new AdminStub(); }
        @Bean @Primary RestClient adminRestClient(AdminStub stub) { return stub.builder.build(); }
    }
    static class AdminStub {
        final RestClient.Builder builder = RestClient.builder().baseUrl("http://producer.test/api/v1");
        final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    }
    @Autowired MockMvc mvc;
    @Autowired AdminStub stub;
    @BeforeEach void reset() { stub.server.reset(); }

    private MockHttpSession session(String role) {
        var session = new MockHttpSession();
        session.setAttribute(AuthSessionService.ATTRIBUTE, new SessionAuth("admin-token",
                Instant.now().plusSeconds(600), "refresh", Instant.now().plusSeconds(3600),
                3L, "admin", role));
        return session;
    }
    private static final String CATEGORIES = "[{\"id\":1,\"nome\":\"Computers\",\"slug\":\"computers\",\"attiva\":true},{\"id\":2,\"nome\":\"Old\",\"slug\":\"old\",\"attiva\":false}]";
    private static String product(long version, int available, boolean auctionable) {
        return "{\"id\":4,\"sku\":\"LAP-4\",\"nome\":\"Laptop\",\"descrizione\":\"Fast\",\"prezzoFisso\":100.00,"
                + "\"astabile\":" + auctionable + ",\"quantitaDisponibile\":" + available
                + ",\"quantitaBloccata\":2,\"asteProgrammate\":2,\"attivo\":true,"
                + "\"categoria\":{\"id\":1,\"nome\":\"Computers\",\"slug\":\"computers\",\"attiva\":true},\"versione\":" + version + "}";
    }
    private static String page(String content, int number, int pages) {
        return "{\"content\":[" + content + "],\"page\":" + number
                + ",\"size\":100,\"totalElements\":2,\"totalPages\":" + pages + "}";
    }
    private void expect(String path, HttpMethod method, HttpStatus status, String body) {
        stub.server.expect(requestTo("http://producer.test/api/v1" + path))
                .andExpect(method(method)).andExpect(header("Authorization", "Bearer admin-token"))
                .andRespond(withStatus(status).contentType(status.isError()
                        ? MediaType.APPLICATION_PROBLEM_JSON : MediaType.APPLICATION_JSON).body(body));
    }
    private void categories() { expect("/admin/categorie", HttpMethod.GET, HttpStatus.OK, CATEGORIES); }

    @Test void adminOnlyAndCsrf() throws Exception {
        mvc.perform(get("/admin/prodotti")).andExpect(status().is3xxRedirection());
        mvc.perform(get("/admin/prodotti").session(session("USER"))).andExpect(status().isForbidden());
        mvc.perform(post("/admin/prodotti").session(session("USER")).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(post("/admin/aste").session(session("ADMIN"))).andExpect(status().isForbidden());
        stub.server.verify();
    }

    @Test void filteredCatalogPaginationAndReadonlyBlockedStock() throws Exception {
        categories();
        expect("/admin/prodotti?query=Laptop&categoria=computers&astabile=true&attivo=false&page=1&size=100",
                HttpMethod.GET, HttpStatus.OK, page(product(7, 3, true), 1, 3));
        mvc.perform(get("/admin/prodotti").session(session("ADMIN")).param("query", "Laptop")
                        .param("categoria", "computers").param("astabile", "true").param("attivo", "false")
                        .param("page", "1").param("size", "100"))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Blocked")))
                .andExpect(content().string(containsString("Schedule")))
                .andExpect(content().string(containsString("page=2")))
                .andExpect(content().string(not(containsString("admin-token"))));
        stub.server.verify();
    }

    @Test void categoryCreateEditAndProductCreate() throws Exception {
        categories();
        mvc.perform(get("/admin/categorie").session(session("ADMIN")))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Create category")));
        stub.server.reset();
        stub.server.expect(requestTo("http://producer.test/api/v1/admin/categorie"))
                .andExpect(method(HttpMethod.POST)).andExpect(header("Authorization", "Bearer admin-token"))
                .andExpect(org.springframework.test.web.client.match.MockRestRequestMatchers.content().json("{\"nome\":\"Phones\",\"slug\":\"phones\",\"attiva\":false}"))
                .andRespond(withStatus(HttpStatus.CREATED).contentType(MediaType.APPLICATION_JSON).body("{}"));
        mvc.perform(post("/admin/categorie").session(session("ADMIN")).with(csrf())
                        .param("nome", "Phones").param("slug", "phones").param("attiva", "false"))
                .andExpect(redirectedUrl("/admin/categorie"));
        stub.server.verify();
        stub.server.reset();
        categories();
        mvc.perform(get("/admin/categorie/2/modifica").session(session("ADMIN")))
                .andExpect(status().isOk()).andExpect(content().string(containsString("value=\"old\"")));
        stub.server.reset();
        stub.server.expect(requestTo("http://producer.test/api/v1/admin/categorie/2"))
                .andExpect(method(HttpMethod.PUT)).andExpect(header("Authorization", "Bearer admin-token"))
                .andExpect(org.springframework.test.web.client.match.MockRestRequestMatchers.content().json("{\"nome\":\"Old\",\"slug\":\"old\",\"attiva\":true}"))
                .andRespond(withStatus(HttpStatus.OK).contentType(MediaType.APPLICATION_JSON).body("{}"));
        mvc.perform(post("/admin/categorie/2").session(session("ADMIN")).with(csrf())
                        .param("nome", "Old").param("slug", "old").param("attiva", "true"))
                .andExpect(redirectedUrl("/admin/categorie"));
        stub.server.verify();

        stub.server.reset();
        categories();
        mvc.perform(get("/admin/prodotti/nuovo").session(session("ADMIN"))).andExpect(status().isOk())
                .andExpect(content().string(containsString("Create product")));
        stub.server.reset();
        stub.server.expect(requestTo("http://producer.test/api/v1/admin/prodotti"))
                .andExpect(method(HttpMethod.POST)).andExpect(header("Authorization", "Bearer admin-token"))
                .andExpect(org.springframework.test.web.client.match.MockRestRequestMatchers.content().json("{\"categoriaId\":1,\"sku\":\"LAP-4\",\"nome\":\"Laptop\","
                        + "\"astabile\":true,\"quantitaDisponibile\":3}"))
                .andRespond(withStatus(HttpStatus.CREATED).contentType(MediaType.APPLICATION_JSON).body(product(0, 3, true)));
        mvc.perform(post("/admin/prodotti").session(session("ADMIN")).with(csrf())
                        .param("categoriaId", "1").param("sku", "LAP-4").param("nome", "Laptop")
                        .param("astabile", "true").param("quantitaDisponibile", "3")
                        .param("attivo", "true").param("versione", "0"))
                .andExpect(redirectedUrl("/admin/prodotti"));
        stub.server.verify();
    }

    @Test void fullProductUpdateAndConflictReload() throws Exception {
        expect("/admin/prodotti/4", HttpMethod.GET, HttpStatus.OK, product(7, 3, true));
        categories();
        mvc.perform(get("/admin/prodotti/4/modifica").session(session("ADMIN")))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Blocked stock:")))
                .andExpect(content().string(containsString("name=\"versione\" value=\"7\"")));
        stub.server.reset();
        stub.server.expect(requestTo("http://producer.test/api/v1/admin/prodotti/4"))
                .andExpect(method(HttpMethod.PUT)).andExpect(header("Authorization", "Bearer admin-token"))
                .andExpect(org.springframework.test.web.client.match.MockRestRequestMatchers.content().json("{\"categoriaId\":1,\"sku\":\"LAP-4\",\"nome\":\"New\","
                        + "\"descrizione\":\"Edited\",\"prezzoFisso\":99.50,\"astabile\":false,"
                        + "\"quantitaDisponibile\":5,\"attivo\":false,\"versione\":7}"))
                .andRespond(withStatus(HttpStatus.CONFLICT).contentType(MediaType.APPLICATION_PROBLEM_JSON)
                        .body("{\"code\":\"VERSIONE_NON_AGGIORNATA\"}"));
        expect("/admin/prodotti/4", HttpMethod.GET, HttpStatus.OK, product(8, 2, true));
        categories();
        mvc.perform(post("/admin/prodotti/4").session(session("ADMIN")).with(csrf())
                        .param("categoriaId", "1").param("sku", "LAP-4").param("nome", "New")
                        .param("descrizione", "Edited").param("prezzoFisso", "99.50")
                        .param("astabile", "false").param("quantitaDisponibile", "5")
                        .param("attivo", "false").param("versione", "7"))
                .andExpect(status().isConflict())
                .andExpect(content().string(containsString("Your attempted changes")))
                .andExpect(content().string(containsString("name=\"versione\" value=\"8\"")))
                .andExpect(content().string(containsString("value=\"2\"")));
        stub.server.verify();
    }

    @Test void scheduleEligibleAcrossPagesAndHandleStockRace() throws Exception {
        expect("/admin/prodotti?astabile=true&page=0&size=100", HttpMethod.GET, HttpStatus.OK,
                page(product(7, 0, true), 0, 2));
        expect("/admin/prodotti?astabile=true&page=1&size=100", HttpMethod.GET, HttpStatus.OK,
                page(product(7, 3, true), 1, 2));
        mvc.perform(get("/admin/aste/nuova").session(session("ADMIN"))).andExpect(status().isOk())
                .andExpect(content().string(containsString("3 available")))
                .andExpect(content().string(not(containsString("0 available"))))
                .andExpect(content().string(containsString("Europe/Rome")));
        stub.server.reset();
        stub.server.expect(requestTo("http://producer.test/api/v1/admin/aste"))
                .andExpect(method(HttpMethod.POST)).andExpect(header("Authorization", "Bearer admin-token"))
                .andExpect(org.springframework.test.web.client.match.MockRestRequestMatchers.content().json("{\"prodottoId\":4,\"inizioLocale\":\"2026-11-10T18:30:00\","
                        + "\"timeZone\":\"Europe/Rome\",\"prezzoIniziale\":50.00}"))
                .andRespond(withStatus(HttpStatus.CONFLICT).contentType(MediaType.APPLICATION_PROBLEM_JSON)
                        .body("{\"code\":\"PRODOTTO_NON_DISPONIBILE\"}"));
        expect("/admin/prodotti?astabile=true&page=0&size=100", HttpMethod.GET, HttpStatus.OK,
                page(product(8, 0, true), 0, 1));
        mvc.perform(post("/admin/aste").session(session("ADMIN")).with(csrf())
                        .param("prodottoId", "4").param("inizioLocale", "2026-11-10T18:30")
                        .param("prezzoIniziale", "50.00"))
                .andExpect(status().isConflict()).andExpect(content().string(containsString("No stock remains")));
        stub.server.verify();
    }

    @Test void scheduleSuccessAndProducerDowntime() throws Exception {
        stub.server.expect(requestTo("http://producer.test/api/v1/admin/aste"))
                .andExpect(method(HttpMethod.POST)).andExpect(header("Authorization", "Bearer admin-token"))
                .andExpect(org.springframework.test.web.client.match.MockRestRequestMatchers.content().json("{\"prodottoId\":4,\"timeZone\":\"Europe/Rome\",\"prezzoIniziale\":50.00}"))
                .andRespond(withStatus(HttpStatus.CREATED).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"id\":42,\"stato\":\"PROGRAMMATA\"}"));
        mvc.perform(post("/admin/aste").session(session("ADMIN")).with(csrf())
                        .param("prodottoId", "4").param("inizioLocale", "2026-11-10T18:30")
                        .param("prezzoIniziale", "50.00")).andExpect(redirectedUrl("/aste"));
        stub.server.verify();
        stub.server.reset();
        expect("/admin/categorie", HttpMethod.GET, HttpStatus.SERVICE_UNAVAILABLE, "{}");
        mvc.perform(get("/admin/prodotti").session(session("ADMIN")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().string(containsString("temporarily unavailable")));
        stub.server.verify();
    }

    @Test void scheduleDomainErrorsAndInvalidFormStayOnPage() throws Exception {
        String[] codes = {"PRODOTTO_NON_ASTABILE", "DATA_INIZIO_NON_VALIDA",
                "PREZZO_INIZIALE_NON_VALIDO", "RISORSA_NON_TROVATA"};
        String[] messages = {"no longer eligible", "start time is invalid",
                "starting price is invalid", "no longer exists"};
        for (int i = 0; i < codes.length; i++) {
            stub.server.reset();
            HttpStatus status = i == 3 ? HttpStatus.NOT_FOUND : HttpStatus.UNPROCESSABLE_ENTITY;
            expect("/admin/aste", HttpMethod.POST, status, "{\"code\":\"" + codes[i] + "\"}");
            expect("/admin/prodotti?astabile=true&page=0&size=100", HttpMethod.GET,
                    HttpStatus.OK, page(product(7, 3, true), 0, 1));
            mvc.perform(post("/admin/aste").session(session("ADMIN")).with(csrf())
                            .param("prodottoId", "4").param("inizioLocale", "2026-11-10T18:30")
                            .param("prezzoIniziale", "50.00"))
                    .andExpect(status().is(status.value()))
                    .andExpect(content().string(containsString(messages[i])));
            stub.server.verify();
        }
        stub.server.reset();
        expect("/admin/prodotti?astabile=true&page=0&size=100", HttpMethod.GET,
                HttpStatus.OK, page(product(7, 3, true), 0, 1));
        mvc.perform(post("/admin/aste").session(session("ADMIN")).with(csrf())
                        .param("prodottoId", "4").param("inizioLocale", "invalid")
                        .param("prezzoIniziale", "-1"))
                .andExpect(status().isBadRequest()).andExpect(content().string(containsString("positive price")));
        stub.server.verify();
    }
}
