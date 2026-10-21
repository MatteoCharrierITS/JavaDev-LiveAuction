package it.esercitazione.liveauction.producer.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
import java.util.List;
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
                "app.aste.scheduler.enabled=false",
                "app.websocket.allowed-origins=http://localhost:8082,http://vpn-test:8082"})
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

    @ParameterizedTest
    @ValueSource(strings = {"http://localhost:8082", "http://vpn-test:8082"})
    void joinSendsPrivateSnapshotAndPublicPresenceWithoutSequence(String origin) throws Exception {
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
            headers.setOrigin(origin);
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

    @Test
    void adminObserverReceivesPresenceButIsNotListedAmongParticipants() throws Exception {
        Account user = account();
        Account admin = account();
        jdbc.update("UPDATE utenti SET ruolo = 'ADMIN' WHERE id = ?", admin.id());
        long categoryId = data.categoryId = jdbc.queryForObject(
                "INSERT INTO categorie (nome, slug) VALUES (?, ?) RETURNING id",
                Long.class, "WebSocket Observer " + admin.id(), "wso-" + admin.id());
        long productId = data.productId = jdbc.queryForObject("""
                INSERT INTO prodotti (categoria_id, sku, nome, astabile, quantita_disponibile)
                VALUES (?, ?, ?, TRUE, 1) RETURNING id
                """, Long.class, categoryId, "SKU-WSO-" + admin.id(), "WebSocket Observer");
        Instant now = Instant.now();
        long auctionId = data.auctionId = jdbc.queryForObject("""
                INSERT INTO aste (prodotto_id, admin_id, inizio_at, fine_at, prezzo_iniziale)
                VALUES (?, ?, ?, ?, 10.00) RETURNING id
                """, Long.class, productId, admin.id(),
                Timestamp.from(now.plusSeconds(120)), Timestamp.from(now.plusSeconds(540)));

        WebSocketStompClient client = new WebSocketStompClient(new StandardWebSocketClient());
        client.setMessageConverter(new MappingJackson2MessageConverter());
        try {
            BlockingQueue<Map<?, ?>> userPrivate = new LinkedBlockingQueue<>();
            BlockingQueue<Map<?, ?>> userRoom = new LinkedBlockingQueue<>();
            StompSession userSession = join(client, ticket(user, auctionId), auctionId, userPrivate, userRoom);
            assertThat(userPrivate.poll(10, TimeUnit.SECONDS)).isNotNull();
            Map<?, ?> joined = userRoom.poll(10, TimeUnit.SECONDS);
            assertThat(joined).isNotNull();
            assertThat(joined.get("type")).isEqualTo("USER_JOINED");

            BlockingQueue<Map<?, ?>> adminPrivate = new LinkedBlockingQueue<>();
            BlockingQueue<Map<?, ?>> adminRoom = new LinkedBlockingQueue<>();
            StompSession adminSession = join(client, ticket(admin, auctionId), auctionId, adminPrivate, adminRoom);
            Map<?, ?> snapshot = adminPrivate.poll(10, TimeUnit.SECONDS);
            assertThat(snapshot).isNotNull();
            assertThat(snapshot.get("type")).isEqualTo("PRESENCE_SNAPSHOT");
            assertThat(snapshot.get("participantCount")).isEqualTo(1);
            assertThat((List<?>) snapshot.get("participants")).hasSize(1);
            // Nessun USER_JOINED o USER_LEFT per l'osservatore.
            assertThat(userRoom.poll(500, TimeUnit.MILLISECONDS)).isNull();
            adminSession.disconnect();
            assertThat(userRoom.poll(500, TimeUnit.MILLISECONDS)).isNull();
            userSession.disconnect();
        } finally {
            client.stop();
        }
    }

    private Account account() throws Exception {
        String username = "wp" + UUID.randomUUID().toString().substring(0, 12);
        data.usernames.add(username);
        mvc.perform(post("/api/v1/auth/register").contentType("application/json")
                        .content(json.writeValueAsString(Map.of("username", username,
                                "email", username + "@example.com", "password", "TestPassword123!"))))
                .andExpect(status().isCreated());
        JsonNode login = json.readTree(mvc.perform(post("/api/v1/auth/login").contentType("application/json")
                        .content(json.writeValueAsString(Map.of("username", username,
                                "password", "TestPassword123!"))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        return new Account(login.get("userId").asLong(), "Bearer " + login.get("accessToken").asText());
    }

    private String ticket(Account account, long auctionId) throws Exception {
        return json.readTree(mvc.perform(post("/api/v1/aste/" + auctionId + "/ticket")
                        .header("Authorization", account.bearer()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()).get("ticket").asText();
    }

    private StompSession join(WebSocketStompClient client, String ticket, long auctionId,
                              BlockingQueue<Map<?, ?>> privateMessages,
                              BlockingQueue<Map<?, ?>> roomMessages) throws Exception {
        WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
        headers.setOrigin("http://localhost:8082");
        StompSession session = client.connectAsync("ws://localhost:" + port + "/ws?ticket=" + ticket,
                headers, new StompSessionHandlerAdapter() {}).get(10, TimeUnit.SECONDS);
        session.subscribe("/user/queue/aste", frames(privateMessages));
        session.subscribe("/topic/aste/" + auctionId, frames(roomMessages));
        session.send("/app/aste/" + auctionId + "/join", Map.of());
        return session;
    }

    private record Account(long id, String bearer) {}

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
