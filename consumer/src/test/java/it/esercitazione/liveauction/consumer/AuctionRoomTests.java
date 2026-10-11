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
class AuctionRoomTests {
    @TestConfiguration
    static class StubConfig {
        @Bean RoomStub roomStub() { return new RoomStub(); }
        @Bean @Primary RestClient roomRestClient(RoomStub stub) { return stub.builder.build(); }
    }
    static class RoomStub {
        final RestClient.Builder builder = RestClient.builder().baseUrl("http://producer.test/api/v1");
        final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    }

    @Autowired MockMvc mvc;
    @Autowired RoomStub stub;
    @BeforeEach void reset() { stub.server.reset(); }

    private MockHttpSession session(String role) {
        var session = new MockHttpSession();
        session.setAttribute(AuthSessionService.ATTRIBUTE, new SessionAuth("secret-access-token",
                Instant.now().plusSeconds(600), "secret-refresh-token", Instant.now().plusSeconds(3600),
                7L, "buyer", role));
        return session;
    }

    private void expect(String path, HttpMethod method, HttpStatus status, String body, boolean protectedCall) {
        var expected = stub.server.expect(requestTo("http://producer.test/api/v1" + path))
                .andExpect(method(method));
        if (protectedCall) expected.andExpect(header("Authorization", "Bearer secret-access-token"));
        expected.andRespond(withStatus(status).contentType(status.isError()
                ? MediaType.APPLICATION_PROBLEM_JSON : MediaType.APPLICATION_JSON).body(body));
    }

    private String snapshot() {
        return "{\"id\":42,\"stato\":\"STANZA_APERTA\",\"prodotto\":{\"id\":3,\"nome\":\"Lamp\"},"
                + "\"prezzoIniziale\":\"50.00\",\"incrementoMinimo\":\"1.00\",\"offertaCorrente\":null,"
                + "\"migliorOfferente\":null,\"numeroOfferte\":0,\"aperturaStanzaAt\":\"2026-10-11T16:57:00Z\","
                + "\"inizioAt\":\"2026-10-11T17:00:00Z\",\"fineAt\":\"2026-10-11T17:07:00Z\","
                + "\"serverTime\":\"2026-10-11T16:58:00Z\",\"offerteConsentite\":false,\"sequence\":1}";
    }

    @Test void onlyUsersCanOpenRoomOrRequestTicket() throws Exception {
        mvc.perform(get("/aste/42")).andExpect(redirectedUrl("/login?expired=0"));
        mvc.perform(get("/aste/42/snapshot")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SESSION_EXPIRED"));
        mvc.perform(get("/aste/42/wallet")).andExpect(status().isUnauthorized());
        mvc.perform(get("/aste/42").session(session("ADMIN"))).andExpect(status().isForbidden());
        mvc.perform(post("/aste/42/ticket").session(session("ADMIN")).with(csrf()))
                .andExpect(status().isForbidden());
        mvc.perform(post("/aste/42/ticket").session(session("USER")))
                .andExpect(status().isForbidden());
        stub.server.verify();
    }

    @Test void roomAndSnapshotUseProducerStateWithoutExposingCredentials() throws Exception {
        expect("/aste/42", HttpMethod.GET, HttpStatus.OK, snapshot(), false);
        expect("/aste/42", HttpMethod.GET, HttpStatus.OK, snapshot(), false);
        mvc.perform(get("/aste/42").session(session("USER")))
                .andExpect(status().isOk()).andExpect(view().name("auctions/room"))
                .andExpect(content().string(containsString("Lamp")))
                .andExpect(content().string(containsString("data-csrf-header=")))
                .andExpect(content().string(not(containsString("secret-access-token"))))
                .andExpect(content().string(not(containsString("secret-refresh-token"))));
        mvc.perform(get("/aste/42/snapshot").session(session("USER")))
                .andExpect(status().isOk()).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Cache-Control", containsString("no-store")))
                .andExpect(jsonPath("$.sequence").value(1));
        stub.server.verify();
    }

    @Test void ticketIsSingleFieldAndEarlyProducerRejectionIsPreserved() throws Exception {
        expect("/aste/42/ticket", HttpMethod.POST, HttpStatus.CONFLICT,
                "{\"code\":\"STANZA_NON_APERTA\"}", true);
        expect("/aste/42/ticket", HttpMethod.POST, HttpStatus.OK,
                "{\"ticket\":\"short-ticket\",\"expiresAt\":\"2026-10-11T16:58:30Z\"}", true);
        mvc.perform(post("/aste/42/ticket").session(session("USER")).with(csrf()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("STANZA_NON_APERTA"));
        mvc.perform(post("/aste/42/ticket").session(session("USER")).with(csrf()))
                .andExpect(status().isOk()).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Cache-Control", containsString("no-store")))
                .andExpect(jsonPath("$.ticket").value("short-ticket"))
                .andExpect(jsonPath("$.expiresAt").doesNotExist());
        stub.server.verify();
    }

    @Test void walletUsesRealProtectedEndpointAndReturnsOnlyBalances() throws Exception {
        expect("/me/portafoglio?page=0&size=1", HttpMethod.GET, HttpStatus.OK,
                "{\"saldoTotale\":100,\"saldoRiservato\":20,\"saldoDisponibile\":80,"
                        + "\"valuta\":\"CRD\",\"movimenti\":[],\"page\":0,\"size\":1,"
                        + "\"totalElements\":0,\"totalPages\":0}", true);
        mvc.perform(get("/aste/42/wallet").session(session("USER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.saldoDisponibile").value(80))
                .andExpect(jsonPath("$.movimenti").doesNotExist());
        stub.server.verify();
    }
}
