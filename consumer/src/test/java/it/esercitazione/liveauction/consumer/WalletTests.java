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
class WalletTests {
    @TestConfiguration
    static class StubConfig {
        @Bean WalletStub walletStub() { return new WalletStub(); }
        @Bean @Primary RestClient walletRestClient(WalletStub stub) { return stub.builder.build(); }
    }

    static class WalletStub {
        final RestClient.Builder builder = RestClient.builder().baseUrl("http://producer.test/api/v1");
        final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    }

    @Autowired MockMvc mvc;
    @Autowired WalletStub stub;

    @BeforeEach void reset() { stub.server.reset(); }

    private MockHttpSession session(String role) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(AuthSessionService.ATTRIBUTE, new SessionAuth("wallet-token",
                Instant.now().plusSeconds(600), "wallet-refresh", Instant.now().plusSeconds(3600),
                7L, "alice", role));
        return session;
    }

    private String page(String movements, int page, int size, long total, long pages, String balance) {
        return "{\"saldoTotale\":" + balance + ",\"saldoRiservato\":0.00,\"saldoDisponibile\":"
                + balance + ",\"valuta\":\"CRD\",\"movimenti\":[" + movements + "],\"page\":" + page
                + ",\"size\":" + size + ",\"totalElements\":" + total + ",\"totalPages\":" + pages + "}";
    }

    private void expectGet(int page, int size, HttpStatus status, String body) {
        stub.server.expect(requestTo("http://producer.test/api/v1/me/portafoglio?page=" + page + "&size=" + size))
                .andExpect(method(org.springframework.http.HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer wallet-token"))
                .andRespond(withStatus(status).contentType(status.isError() ? MediaType.APPLICATION_PROBLEM_JSON
                        : MediaType.APPLICATION_JSON).body(body));
    }

    private void expectPut(String submitted, HttpStatus status, String body) {
        stub.server.expect(requestTo("http://producer.test/api/v1/me/portafoglio/impostazioni"))
                .andExpect(method(org.springframework.http.HttpMethod.PUT))
                .andExpect(header("Authorization", "Bearer wallet-token"))
                .andExpect(org.springframework.test.web.client.match.MockRestRequestMatchers.content()
                        .json("{\"saldoTotale\":" + submitted + "}"))
                .andRespond(withStatus(status).contentType(status.isError() ? MediaType.APPLICATION_PROBLEM_JSON
                        : MediaType.APPLICATION_JSON).body(body));
    }

    @Test void routeIsUserOnlyAndUpdateRequiresCsrf() throws Exception {
        mvc.perform(get("/impostazioni/portafoglio")).andExpect(redirectedUrl("/login?expired=0"));
        mvc.perform(get("/impostazioni/portafoglio").session(session("ADMIN"))).andExpect(status().isForbidden());
        mvc.perform(post("/impostazioni/portafoglio").session(session("ADMIN")).with(csrf())
                .param("saldoTotale", "1.00")).andExpect(status().isForbidden());
        mvc.perform(post("/impostazioni/portafoglio").session(session("USER"))
                .param("saldoTotale", "1.00")).andExpect(status().isForbidden());
        stub.server.verify();
    }

    @Test void zeroBalanceAndEmptyLedgerComeFromProducer() throws Exception {
        expectGet(0, 20, HttpStatus.OK, page("", 0, 20, 0, 0, "0.00"));
        mvc.perform(get("/impostazioni/portafoglio").session(session("USER")))
                .andExpect(status().isOk()).andExpect(view().name("wallet/page"))
                .andExpect(content().string(containsString("0,00 CRD")))
                .andExpect(content().string(containsString("No movements yet.")))
                .andExpect(content().string(containsString("absolute amount replaces the current total")))
                .andExpect(content().string(containsString("name=\"_csrf\"")))
                .andExpect(content().string(not(containsString("wallet-token"))));
        stub.server.verify();
    }

    @Test void paginationMovementTypesItalianAmountsAndRomeTime() throws Exception {
        String movement = "{\"id\":42,\"astaId\":7,\"tipo\":\"RISERVA_OFFERTA\",\"importo\":1250.00,"
                + "\"saldoTotaleDopo\":10000.00,\"saldoRiservatoDopo\":1250.00,"
                + "\"dataMovimento\":\"2026-10-09T07:00:00Z\"}";
        expectGet(1, 1, HttpStatus.OK, "{\"saldoTotale\":10000.00,\"saldoRiservato\":1250.00,"
                + "\"saldoDisponibile\":8750.00,\"valuta\":\"CRD\",\"movimenti\":[" + movement
                + "],\"page\":1,\"size\":1,\"totalElements\":3,\"totalPages\":3}");
        mvc.perform(get("/impostazioni/portafoglio").session(session("USER"))
                        .param("page", "1").param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("10.000,00 CRD")))
                .andExpect(content().string(containsString("1.250,00 CRD")))
                .andExpect(content().string(containsString("8.750,00 CRD")))
                .andExpect(content().string(containsString("Bid reserved")))
                .andExpect(content().string(containsString("09/10/2026 09:00")))
                .andExpect(content().string(containsString("page=0&amp;size=1")))
                .andExpect(content().string(containsString("page=2&amp;size=1")));
        stub.server.verify();
    }

    @Test void identicalBalancePutThenFreshGet() throws Exception {
        MockHttpSession session = session("USER");
        expectPut("100.00", HttpStatus.OK,
                "{\"saldoTotale\":100.00,\"saldoRiservato\":0.00,\"saldoDisponibile\":100.00,\"valuta\":\"CRD\"}");
        expectGet(0, 20, HttpStatus.OK, page("", 0, 20, 0, 0, "100.00"));
        mvc.perform(post("/impostazioni/portafoglio").session(session).with(csrf())
                        .param("saldoTotale", "100.00"))
                .andExpect(redirectedUrl("/impostazioni/portafoglio?page=0&size=20"));
        mvc.perform(get("/impostazioni/portafoglio").session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("100,00 CRD")));
        stub.server.verify();
    }

    @Test void invalidAmountAndProducerValidationShowCurrentBalance() throws Exception {
        expectGet(0, 20, HttpStatus.OK, page("", 0, 20, 0, 0, "0.00"));
        mvc.perform(post("/impostazioni/portafoglio").session(session("USER")).with(csrf())
                        .param("saldoTotale", "-1.00"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("at most two decimal places")));
        stub.server.verify();

        stub.server.reset();
        expectPut("1.00", HttpStatus.BAD_REQUEST, "{\"code\":\"IMPORTO_NON_VALIDO\"}");
        expectGet(0, 20, HttpStatus.OK, page("", 0, 20, 0, 0, "0.00"));
        mvc.perform(post("/impostazioni/portafoglio").session(session("USER")).with(csrf())
                        .param("saldoTotale", "1.00"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("Producer rejected this amount")));
        stub.server.verify();
    }

    @Test void reserveConflictShowsFreshReservedBalance() throws Exception {
        expectPut("5.00", HttpStatus.CONFLICT, "{\"code\":\"SALDO_INFERIORE_AL_RISERVATO\"}");
        expectGet(0, 20, HttpStatus.OK, "{\"saldoTotale\":10.00,\"saldoRiservato\":8.00,"
                + "\"saldoDisponibile\":2.00,\"valuta\":\"CRD\",\"movimenti\":[],"
                + "\"page\":0,\"size\":20,\"totalElements\":0,\"totalPages\":0}");
        mvc.perform(post("/impostazioni/portafoglio").session(session("USER")).with(csrf())
                        .param("saldoTotale", "5.00"))
                .andExpect(status().isConflict())
                .andExpect(content().string(containsString("lower than the currently reserved balance")))
                .andExpect(content().string(containsString("8,00 CRD")));
        stub.server.verify();
    }

    @Test void producerAuthRoleAndMissingWalletErrors() throws Exception {
        expectGet(0, 20, HttpStatus.FORBIDDEN, "{\"status\":403}");
        mvc.perform(get("/impostazioni/portafoglio").session(session("USER")))
                .andExpect(status().isForbidden()).andExpect(content().string(containsString("Access denied")));
        stub.server.verify();
        stub.server.reset();
        expectGet(0, 20, HttpStatus.NOT_FOUND, "{\"code\":\"RISORSA_NON_TROVATA\"}");
        mvc.perform(get("/impostazioni/portafoglio").session(session("USER")))
                .andExpect(status().isNotFound()).andExpect(content().string(containsString("Wallet missing")));
        stub.server.verify();
        stub.server.reset();
        expectGet(0, 20, HttpStatus.UNAUTHORIZED, "{\"status\":401}");
        stub.server.expect(requestTo("http://producer.test/api/v1/auth/refresh"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED).contentType(MediaType.APPLICATION_PROBLEM_JSON)
                        .body("{\"status\":401}"));
        MockHttpSession revoked = session("USER");
        mvc.perform(get("/impostazioni/portafoglio").session(revoked))
                .andExpect(redirectedUrl("/login?expired=1"));
        org.assertj.core.api.Assertions.assertThat(revoked.isInvalid()).isTrue();
        stub.server.verify();
    }
}
