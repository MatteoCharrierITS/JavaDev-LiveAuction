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
class AuctionHistoryConsumerTests {
    @TestConfiguration
    static class StubConfig {
        @Bean HistoryStub historyStub() { return new HistoryStub(); }
        @Bean @Primary RestClient historyRestClient(HistoryStub stub) { return stub.builder.build(); }
    }
    static class HistoryStub {
        final RestClient.Builder builder = RestClient.builder().baseUrl("http://producer.test/api/v1");
        final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    }
    @Autowired MockMvc mvc;
    @Autowired HistoryStub stub;
    @BeforeEach void reset() { stub.server.reset(); }

    private MockHttpSession session(String role) {
        var session = new MockHttpSession();
        session.setAttribute(AuthSessionService.ATTRIBUTE, new SessionAuth("history-token",
                Instant.now().plusSeconds(600), "refresh", Instant.now().plusSeconds(3600),
                8L, "viewer", role));
        return session;
    }

    private void expect(String path, HttpMethod method, HttpStatus status, String body) {
        stub.server.expect(requestTo("http://producer.test/api/v1" + path))
                .andExpect(method(method)).andExpect(header("Authorization", "Bearer history-token"))
                .andRespond(withStatus(status).contentType(status.isError()
                        ? MediaType.APPLICATION_PROBLEM_JSON : MediaType.APPLICATION_JSON).body(body));
    }
    private String page(String entries, int page, int pages) {
        return "{\"content\":[" + entries + "],\"page\":" + page
                + ",\"size\":20,\"totalElements\":" + (entries.isEmpty() ? 0 : 1)
                + ",\"totalPages\":" + pages + "}";
    }

    @Test void authorizationAndCsrf() throws Exception {
        mvc.perform(get("/me/vittorie")).andExpect(status().is3xxRedirection());
        mvc.perform(get("/me/vittorie").session(session("ADMIN"))).andExpect(status().isForbidden());
        mvc.perform(get("/admin/aste/storico").session(session("USER"))).andExpect(status().isForbidden());
        mvc.perform(post("/admin/aste/18/annullamento").session(session("ADMIN"))).andExpect(status().isForbidden());
        mvc.perform(post("/admin/aste/18/annullamento").session(session("USER")).with(csrf()))
                .andExpect(status().isForbidden());
        stub.server.verify();
    }

    @Test void victoriesRenderAndPaginate() throws Exception {
        expect("/me/vittorie?page=1&size=20", HttpMethod.GET, HttpStatus.OK,
                page("{\"id\":18,\"prodotto\":{\"id\":3,\"nome\":\"Keyboard\"},"
                        + "\"prezzoFinale\":75.00,\"chiusaAt\":\"2026-10-12T12:37:20Z\"}", 1, 3));
        mvc.perform(get("/me/vittorie").session(session("USER")).param("page", "1"))
                .andExpect(status().isOk()).andExpect(view().name("auction/victories"))
                .andExpect(content().string(containsString("Keyboard")))
                .andExpect(content().string(containsString("page=2")))
                .andExpect(content().string(not(containsString("history-token"))));
        stub.server.verify();
    }

    @Test void victoriesEmptyAndInvalidPage() throws Exception {
        expect("/me/vittorie?page=0&size=20", HttpMethod.GET, HttpStatus.OK, page("", 0, 0));
        mvc.perform(get("/me/vittorie").session(session("USER"))).andExpect(status().isOk())
                .andExpect(content().string(containsString("No victories on this page")));
        mvc.perform(get("/me/vittorie").session(session("USER")).param("size", "101"))
                .andExpect(status().isBadRequest());
        stub.server.verify();
    }

    @Test void historyFiltersRenderAndEmpty() throws Exception {
        expect("/admin/aste/storico?prodottoId=3&stato=ANNULLATA&page=0&size=20", HttpMethod.GET,
                HttpStatus.OK, page("{\"id\":18,\"stato\":\"ANNULLATA\","
                        + "\"prodotto\":{\"id\":3,\"nome\":\"Keyboard\"},"
                        + "\"vincitoreId\":null,\"prezzoFinale\":null,"
                        + "\"inizioAt\":\"2026-10-12T12:30:00Z\","
                        + "\"fineAt\":\"2026-10-12T12:37:00Z\","
                        + "\"conclusaAt\":\"2026-10-12T12:31:00Z\"}", 0, 1));
        mvc.perform(get("/admin/aste/storico").session(session("ADMIN"))
                        .param("prodottoId", "3").param("stato", "ANNULLATA"))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Keyboard")))
                .andExpect(content().string(containsString("No winner")));
        stub.server.verify();
        stub.server.reset();
        expect("/admin/aste/storico?page=0&size=20", HttpMethod.GET, HttpStatus.OK, page("", 0, 0));
        mvc.perform(get("/admin/aste/storico").session(session("ADMIN")))
                .andExpect(status().isOk()).andExpect(content().string(containsString("No auctions match")));
        mvc.perform(get("/admin/aste/storico").session(session("ADMIN")).param("stato", "APERTA"))
                .andExpect(status().isBadRequest());
        stub.server.verify();
    }

    @Test void cancellationOutcomeAndProblemMapping() throws Exception {
        expect("/admin/aste/18/annullamento", HttpMethod.POST, HttpStatus.OK,
                "{\"id\":18,\"stato\":\"ANNULLATA\",\"annullataAt\":\"2026-10-12T12:00:00Z\",\"sequence\":3}");
        mvc.perform(post("/admin/aste/18/annullamento").session(session("ADMIN")).with(csrf()))
                .andExpect(redirectedUrl("/aste")).andExpect(flash().attributeExists("notice"));
        stub.server.verify();
        stub.server.reset();
        expect("/admin/aste/18/annullamento", HttpMethod.POST, HttpStatus.CONFLICT,
                "{\"code\":\"ASTA_CON_OFFERTE\"}");
        mvc.perform(post("/admin/aste/18/annullamento").session(session("ADMIN")).with(csrf()))
                .andExpect(status().isConflict()).andExpect(content().string(containsString("received an offer")));
        stub.server.verify();
    }

    @Test void lobbyOffersCsrfProtectedCancellationOnlyForEligibleAdminView() throws Exception {
        stub.server.expect(requestTo("http://producer.test/api/v1/aste?page=0&size=12"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.OK).contentType(MediaType.APPLICATION_JSON).body(
                        "{\"content\":[{\"id\":18,\"stato\":\"PROGRAMMATA\","
                        + "\"prodotto\":{\"id\":3,\"nome\":\"Keyboard\"},"
                        + "\"prezzoIniziale\":10.00,\"numeroOfferte\":0,"
                        + "\"aperturaStanzaAt\":\"2026-10-12T12:27:00Z\","
                        + "\"inizioAt\":\"2026-10-12T12:30:00Z\","
                        + "\"fineAt\":\"2026-10-12T12:37:00Z\"}],"
                        + "\"page\":0,\"size\":12,\"totalElements\":1,\"totalPages\":1,"
                        + "\"serverTime\":\"2026-10-12T12:00:00Z\"}"));
        mvc.perform(get("/aste").param("partial", "true").session(session("ADMIN"))).andExpect(status().isOk())
                .andExpect(content().string(containsString("/admin/aste/18/annullamento")))
                .andExpect(content().string(containsString("name=\"_csrf\"")));
        stub.server.verify();
    }

    @Test void historyProblemAndInvalidDateAreVisible() throws Exception {
        mvc.perform(get("/admin/aste/storico").session(session("ADMIN"))
                .param("da", "2026-10-12T00:00:00")).andExpect(status().isBadRequest());
        expect("/admin/aste/storico?page=0&size=20", HttpMethod.GET, HttpStatus.BAD_REQUEST,
                "{\"code\":\"PARAMETRI_NON_VALIDI\"}");
        mvc.perform(get("/admin/aste/storico").session(session("ADMIN")))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("Producer rejected these filters")));
        stub.server.verify();
    }

    @Test void timeRangeIsPassedToProducerAndVictoryForbiddenIsVisible() throws Exception {
        stub.server.expect(requestTo(containsString("/admin/aste/storico?da=2026-10-12T00:00:00Z&a=2026-10-12T23:59:59Z&page=0&size=20")))
                .andExpect(method(HttpMethod.GET)).andExpect(header("Authorization", "Bearer history-token"))
                .andRespond(withStatus(HttpStatus.OK).contentType(MediaType.APPLICATION_JSON).body(page("", 0, 0)));
        mvc.perform(get("/admin/aste/storico").session(session("ADMIN"))
                        .param("da", "2026-10-12T00:00:00Z").param("a", "2026-10-12T23:59:59Z"))
                .andExpect(status().isOk());
        stub.server.verify();
        stub.server.reset();
        expect("/me/vittorie?page=0&size=20", HttpMethod.GET, HttpStatus.FORBIDDEN,
                "{\"title\":\"Forbidden\"}");
        mvc.perform(get("/me/vittorie").session(session("USER")))
                .andExpect(status().isForbidden())
                .andExpect(content().string(containsString("cannot view victories")));
        stub.server.verify();
    }
}
