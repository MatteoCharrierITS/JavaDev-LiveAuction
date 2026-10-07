package it.esercitazione.liveauction.producer.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import it.esercitazione.liveauction.producer.websocket.TicketHandshakeInterceptor;
import it.esercitazione.liveauction.producer.websocket.WebSocketIdentity;
import it.esercitazione.liveauction.producer.websocket.WebSocketTestData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.socket.WebSocketHandler;

import java.net.URI;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {"spring.docker.compose.enabled=false", "app.aste.scheduler.enabled=false"})
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "RUN_DB_TESTS", matches = "true")
class WebSocketTicketIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired TicketHandshakeInterceptor handshake;
    private WebSocketTestData data;

    @BeforeEach
    void preparaPulizia() { data = new WebSocketTestData(jdbc); }

    @AfterEach
    void pulisci() { data.close(); }

    @Test
    void issuesTicketOnlyAfterRoomOpensAndConsumesItOnce() throws Exception {
        String username = "ws_" + UUID.randomUUID().toString().substring(0, 8);
        data.usernames.add(username);
        mvc.perform(post("/api/v1/auth/register")
                        .contentType("application/json")
                        .content(json.writeValueAsString(Map.of(
                                "username", username,
                                "email", username + "@example.com",
                                "password", "TestPassword123!"))))
                .andExpect(status().isCreated());
        JsonNode login = json.readTree(mvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content(json.writeValueAsString(Map.of(
                                "username", username, "password", "TestPassword123!"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        String bearer = "Bearer " + login.get("accessToken").asText();
        long userId = login.get("userId").asLong();

        long categoryId = data.categoryId = jdbc.queryForObject(
                "INSERT INTO categorie (nome, slug) VALUES (?, ?) RETURNING id",
                Long.class, "WebSocket Test " + username, "ws-" + username);
        long productId = data.productId = jdbc.queryForObject("""
                INSERT INTO prodotti (categoria_id, sku, nome, astabile, quantita_disponibile)
                VALUES (?, ?, ?, TRUE, 1) RETURNING id
                """, Long.class, categoryId, "SKU-" + username.replace('_', '-'), "WebSocket Test");
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        long auctionId = data.auctionId = jdbc.queryForObject("""
                INSERT INTO aste (prodotto_id, admin_id, inizio_at, fine_at, prezzo_iniziale)
                VALUES (?, ?, ?, ?, 10.00) RETURNING id
                """, Long.class, productId, userId,
                Timestamp.from(now.plus(4, ChronoUnit.MINUTES)),
                Timestamp.from(now.plus(11, ChronoUnit.MINUTES)));
        String ticketUrl = "/api/v1/aste/" + auctionId + "/ticket";

        mvc.perform(post(ticketUrl)).andExpect(status().isUnauthorized());
        mvc.perform(post(ticketUrl).header("Authorization", bearer))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STANZA_NON_APERTA"));

        jdbc.update("UPDATE aste SET inizio_at = ?, fine_at = ? WHERE id = ?",
                Timestamp.from(now.plus(2, ChronoUnit.MINUTES)),
                Timestamp.from(now.plus(9, ChronoUnit.MINUTES)), auctionId);
        JsonNode ticket = json.readTree(mvc.perform(post(ticketUrl).header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ticket").isString())
                .andExpect(jsonPath("$.expiresAt").isString())
                .andReturn().getResponse().getContentAsString());

        ServerHttpRequest request = mock(ServerHttpRequest.class);
        when(request.getURI()).thenReturn(URI.create("ws://localhost:8081/ws?ticket="
                + ticket.get("ticket").asText()));
        ServerHttpResponse response = mock(ServerHttpResponse.class);
        WebSocketHandler handler = mock(WebSocketHandler.class);
        Map<String, Object> attributes = new HashMap<>();
        assertThat(handshake.beforeHandshake(request, response, handler, attributes)).isTrue();
        assertThat(attributes.get(TicketHandshakeInterceptor.IDENTITY_ATTRIBUTE))
                .isInstanceOf(WebSocketIdentity.class);
        WebSocketIdentity identity = (WebSocketIdentity) attributes.get(
                TicketHandshakeInterceptor.IDENTITY_ATTRIBUTE);
        assertThat(identity.userId()).isEqualTo(userId);
        assertThat(identity.auctionId()).isEqualTo(auctionId);

        assertThat(handshake.beforeHandshake(request, response, handler, new HashMap<>())).isFalse();
        verify(response).setStatusCode(HttpStatus.UNAUTHORIZED);

    }
}
