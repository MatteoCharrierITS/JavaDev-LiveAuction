package it.esercitazione.liveauction.producer.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.lang.reflect.Type;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"spring.docker.compose.enabled=false", "spring.http.client.factory=simple",
                "app.aste.scheduler.enabled=false"})
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "RUN_WS_TESTS", matches = "true")
class PresenceWebSocketIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @LocalServerPort int port;
    private WebSocketTestData data;

    @BeforeEach
    void preparaPulizia() { data = new WebSocketTestData(jdbc); }

    @AfterEach
    void pulisci() { data.close(); }

    @Test
    void joinSendsPrivateSnapshotAndPublicPresenceWithoutSequence() throws Exception {
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
        Instant now = Instant.now();
        long auctionId = data.auctionId = jdbc.queryForObject("""
                INSERT INTO aste (prodotto_id, admin_id, inizio_at, fine_at, prezzo_iniziale)
                VALUES (?, ?, ?, ?, 10.00) RETURNING id
                """, Long.class, productId, userId,
                Timestamp.from(now.plusSeconds(120)), Timestamp.from(now.plusSeconds(540)));
        JsonNode ticket = json.readTree(mvc.perform(post("/api/v1/aste/" + auctionId + "/ticket")
                        .header("Authorization", bearer))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());

        WebSocketStompClient client = new WebSocketStompClient(new StandardWebSocketClient());
        client.setMessageConverter(new MappingJackson2MessageConverter());
        try {
            WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
            headers.setOrigin("http://localhost:8082");
            StompSession session = client.connectAsync("ws://localhost:" + port + "/ws?ticket="
                    + ticket.get("ticket").asText(), headers, new StompSessionHandlerAdapter() {})
                    .get(10, TimeUnit.SECONDS);
            try {
                BlockingQueue<Map<?, ?>> privateMessages = new LinkedBlockingQueue<>();
                BlockingQueue<Map<?, ?>> roomMessages = new LinkedBlockingQueue<>();
                session.subscribe("/user/queue/aste", frames(privateMessages));
                session.subscribe("/topic/aste/" + auctionId, frames(roomMessages));
                session.send("/app/aste/" + auctionId + "/join", Map.of());

                Map<?, ?> snapshot = privateMessages.poll(10, TimeUnit.SECONDS);
                assertThat(snapshot).isNotNull();
                assertThat(snapshot.get("type")).isEqualTo("PRESENCE_SNAPSHOT");
                assertThat(snapshot.get("participantCount")).isEqualTo(1);
                assertThat(snapshot.containsKey("sequence")).isFalse();
                Map<?, ?> joined = roomMessages.poll(10, TimeUnit.SECONDS);
                assertThat(joined).isNotNull();
                assertThat(joined.get("type")).isEqualTo("USER_JOINED");
                assertThat(joined.containsKey("sequence")).isFalse();
            } finally {
                session.disconnect();
            }
        } finally {
            client.stop();
        }
    }

    private static StompFrameHandler frames(BlockingQueue<Map<?, ?>> messages) {
        return new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return Map.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                messages.offer((Map<?, ?>) payload);
            }
        };
    }
}
