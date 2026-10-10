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
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
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
class AuctionLobbyTests {
    @TestConfiguration
    static class StubConfig {
        @Bean AuctionStub auctionStub() { return new AuctionStub(); }
        @Bean @Primary RestClient auctionRestClient(AuctionStub stub) { return stub.builder.build(); }
    }

    static class AuctionStub {
        final RestClient.Builder builder = RestClient.builder().baseUrl("http://producer.test/api/v1");
        final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    }

    @Autowired MockMvc mvc;
    @Autowired AuctionStub stub;

    @BeforeEach void reset() { stub.server.reset(); }

    private void expect(String path, HttpStatus status, String body) {
        stub.server.expect(requestTo("http://producer.test/api/v1" + path))
                .andRespond(withStatus(status).contentType(MediaType.APPLICATION_JSON).body(body));
    }

    private String auction(String state) {
        return "{\"id\":42,\"stato\":\"" + state + "\",\"prodotto\":{\"id\":7,\"nome\":\"Desk lamp\"},"
                + "\"prezzoIniziale\":\"50.00\",\"offertaCorrente\":\"60.00\",\"numeroOfferte\":2,"
                + "\"aperturaStanzaAt\":\"2026-10-10T09:57:00Z\",\"inizioAt\":\"2026-10-10T10:00:00Z\","
                + "\"fineAt\":\"2026-10-10T10:07:00Z\"}";
    }

    private String page(String content, int page, int totalPages, String serverTime) {
        return "{\"content\":[" + content + "],\"page\":" + page + ",\"size\":2,\"totalElements\":5,"
                + "\"totalPages\":" + totalPages + ",\"serverTime\":\"" + serverTime + "\"}";
    }

    @Test void filtersAndPaginationUseProducerParameters() throws Exception {
        expect("/aste?stato=APERTA&categoria=office&query=desk&page=1&size=2", HttpStatus.OK,
                page(auction("APERTA"), 1, 3, "2026-10-10T10:01:00Z"));
        mvc.perform(get("/aste").param("stato", "APERTA").param("categoria", "office")
                        .param("query", "desk").param("page", "1").param("size", "2"))
                .andExpect(status().isOk()).andExpect(view().name("auction/lobby"))
                .andExpect(content().string(containsString("Desk lamp")))
                .andExpect(content().string(containsString("stato=APERTA&amp;categoria=office&amp;query=desk&amp;page=2&amp;size=2")));
        stub.server.verify();
    }

    @Test void everyStateUsesItsOwnCountdownAndOnlyOpenRoomsHaveLinks() throws Exception {
        for (String state : new String[] {"PROGRAMMATA", "STANZA_APERTA", "APERTA", "CHIUSA", "ANNULLATA"}) {
            stub.server.reset();
            expect("/aste?stato=" + state + "&page=0&size=12", HttpStatus.OK,
                    page(auction(state), 0, 1, "2026-10-10T09:58:00Z"));
            var result = mvc.perform(get("/aste").param("stato", state)
                            .with(SecurityMockMvcRequestPostProcessors.user("buyer").roles("USER")))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("data-server-time=\"2026-10-10T09:58:00Z\"")))
                    .andReturn().getResponse().getContentAsString();
            if (state.equals("PROGRAMMATA")) {
                org.junit.jupiter.api.Assertions.assertTrue(result.contains("Room opens in"));
                org.junit.jupiter.api.Assertions.assertTrue(result.contains("data-target=\"2026-10-10T09:57:00Z\""));
            }
            if (state.equals("STANZA_APERTA")) org.junit.jupiter.api.Assertions.assertTrue(result.contains("Auction starts in"));
            if (state.equals("APERTA")) org.junit.jupiter.api.Assertions.assertTrue(result.contains("Ends in"));
            if (state.equals("STANZA_APERTA") || state.equals("APERTA"))
                org.junit.jupiter.api.Assertions.assertTrue(result.contains("href=\"/aste/42\""));
            else org.junit.jupiter.api.Assertions.assertFalse(result.contains("href=\"/aste/42\""));
            stub.server.verify();
        }
    }

    @Test void guestsCanBrowseAndAreDirectedToLogin() throws Exception {
        expect("/aste?page=0&size=12", HttpStatus.OK, page(auction("APERTA"), 0, 1, "2026-10-10T10:01:00Z"));
        mvc.perform(get("/aste")).andExpect(status().isOk())
                .andExpect(content().string(containsString("href=\"/login\"")))
                .andExpect(content().string(not(containsString("href=\"/aste/42\""))));
        stub.server.verify();
    }

    @Test void roomLinkWaitsForServerAccessWindow() throws Exception {
        expect("/aste?page=0&size=12", HttpStatus.OK,
                page(auction("APERTA"), 0, 1, "2026-10-10T09:56:59Z"));
        mvc.perform(get("/aste").with(SecurityMockMvcRequestPostProcessors.user("buyer").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("href=\"/aste/42\""))));
        stub.server.verify();
    }

    @Test void emptyInvalidAndUnavailableAreClear() throws Exception {
        expect("/aste?page=0&size=12", HttpStatus.OK, page("", 0, 0, "2026-10-10T10:01:00Z"));
        mvc.perform(get("/aste")).andExpect(status().isOk())
                .andExpect(content().string(containsString("No auctions match these filters.")));
        mvc.perform(get("/aste").param("stato", "UNKNOWN")).andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("Invalid filters")));
        mvc.perform(get("/aste").param("page", "nope")).andExpect(status().isBadRequest());
        mvc.perform(get("/aste").param("size", "101")).andExpect(status().isBadRequest());
        stub.server.verify();

        stub.server.reset();
        expect("/aste?page=0&size=12", HttpStatus.SERVICE_UNAVAILABLE, "{}");
        mvc.perform(get("/aste")).andExpect(status().isServiceUnavailable())
                .andExpect(content().string(containsString("temporarily unavailable")));
        stub.server.verify();
    }
}
