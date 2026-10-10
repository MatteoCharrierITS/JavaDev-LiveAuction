package it.esercitazione.liveauction.producer.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import it.esercitazione.liveauction.producer.asta.services.AstaLifecycleService;
import it.esercitazione.liveauction.producer.asta.services.ChiusuraAstaService;
import it.esercitazione.liveauction.producer.asta.repos.AstaRepository;
import it.esercitazione.liveauction.producer.asta.schedulers.AstaScheduler;
import it.esercitazione.liveauction.producer.notifica.EmailDeliveryService;
import it.esercitazione.liveauction.producer.notifica.EmailJob;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.converter.CompositeMessageConverter;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.beans.factory.annotation.Qualifier;
import it.esercitazione.liveauction.producer.asta.requests.OffertaRequest;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"spring.docker.compose.enabled=false", "spring.http.client.factory=simple",
                "app.aste.scheduler.enabled=false"})
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "RUN_WS_TESTS", matches = "true")
@EnabledIfEnvironmentVariable(named = "RUN_DB_TESTS", matches = "true")
@EnabledIfEnvironmentVariable(named = "DB_URL", matches = "jdbc:postgresql:.*")
class OfferteWebSocketIntegrationTests {
    private static final LocalSmtpServer SMTP = smtpServer();
    private static LocalSmtpServer smtpServer() {
        try { return new LocalSmtpServer(); }
        catch (java.io.IOException e) { throw new java.io.UncheckedIOException(e); }
    }

    @DynamicPropertySource
    static void smtpProperties(DynamicPropertyRegistry registry) {
        registry.add("app.notifiche.email.enabled", () -> "true");
        registry.add("app.notifiche.email.from", () -> "aste@example.test");
        registry.add("spring.mail.host", () -> "127.0.0.1");
        registry.add("spring.mail.port", SMTP::port);
        registry.add("spring.mail.properties.mail.smtp.auth", () -> "false");
        registry.add("spring.mail.properties.mail.smtp.starttls.enable", () -> "false");
        registry.add("spring.mail.properties.mail.smtp.starttls.required", () -> "false");
        registry.add("spring.mail.properties.mail.smtp.ssl.enable", () -> "false");
    }

    @AfterAll
    static void stopSmtp() throws java.io.IOException { SMTP.close(); }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired AstaLifecycleService lifecycle;
    @Autowired ChiusuraAstaService chiusura;
    @Autowired AstaRepository aste;
    @Autowired EmailDeliveryService delivery;
    @Autowired JavaMailSenderImpl mail;
    // I test invocano il worker esplicitamente, senza invii da job su altre fixture.
    @MockitoBean EmailJob emailJob;
    @Autowired PlatformTransactionManager transactions;
    @Autowired @Qualifier("brokerMessageConverter") CompositeMessageConverter brokerConverter;
    @LocalServerPort int port;

    private WebSocketTestData data;
    private Account primo;
    private Account secondo;
    private Account admin;
    private final List<Connection> connections = new ArrayList<>();

    @BeforeEach
    void prepara() throws Exception {
        SMTP.reset();
        data = new WebSocketTestData(jdbc);
        primo = account();
        secondo = account();
        admin = account();
        jdbc.update("UPDATE utenti SET ruolo = 'ADMIN' WHERE id = ?", admin.id());
        // Saldi di fixture, non una nuova API di ricarica: il test riguarda il trasporto.
        jdbc.update("UPDATE portafogli SET saldo_totale = 1000 WHERE utente_id IN (?, ?)", primo.id(), secondo.id());
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        data.categoryId = jdbc.queryForObject("INSERT INTO categorie(nome, slug) VALUES (?, ?) RETURNING id",
                Long.class, "WS bid " + suffix, "wsbid-" + suffix);
        data.productId = jdbc.queryForObject("""
                INSERT INTO prodotti(categoria_id, sku, nome, astabile, quantita_disponibile, quantita_bloccata)
                VALUES (?, ?, 'WS bid test', TRUE, 0, 1) RETURNING id
                """, Long.class, data.categoryId, "WSBID-" + suffix);
        Instant now = Instant.now();
        data.auctionId = jdbc.queryForObject("""
                INSERT INTO aste(prodotto_id, admin_id, stato, prezzo_iniziale, inizio_at, fine_at)
                VALUES (?, ?, 'APERTA', 10, ?, ?) RETURNING id
                """, Long.class, data.productId, admin.id(), Timestamp.from(now.minusSeconds(5)),
                Timestamp.from(now.plusSeconds(415)));
    }

    @AfterEach
    void pulisci() {
        try {
            for (Connection connection : connections) {
                try {
                    if (connection.session().isConnected()) connection.session().disconnect();
                } finally {
                    connection.client().stop();
                }
            }
        } finally {
            if (data != null) data.close();
        }
    }

    @Test
    void bidsConfirmOnlySendingSessionAndRetryDoesNotRepublishOrMoveFunds() throws Exception {
        Connection first = connect(primo);
        Connection otherTab = connect(primo);
        Connection second = connect(secondo);
        clearRooms();
        UUID id = UUID.randomUUID();
        send(first, id, "10", 0);
        Map<?, ?> accepted = receive(first.room(), "BID_ACCEPTED");
        assertThat(accepted.get("sequence")).isEqualTo(1);
        assertThat(accepted.get("extensionSeconds")).isEqualTo(20);
        assertThat(accepted.get("offerenteDisplay").toString()).contains("***");
        assertThat(accepted.containsKey("migliorOfferenteId")).isFalse();
        Map<?, ?> ack = receive(first.privateMessages(), "BID_CONFIRMED");
        assertThat(ack.get("duplicata")).isEqualTo(false);
        assertThat(otherTab.privateMessages().poll(300, TimeUnit.MILLISECONDS)).isNull();
        clearRooms();

        send(first, id, "10", 0);
        assertThat(receive(first.privateMessages(), "BID_CONFIRMED").get("duplicata")).isEqualTo(true);
        assertThat(first.room().poll(500, TimeUnit.MILLISECONDS)).isNull();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM movimenti_portafoglio WHERE asta_id = ?",
                Integer.class, data.auctionId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT saldo_riservato FROM portafogli WHERE utente_id = ?",
                BigDecimal.class, primo.id())).isEqualByComparingTo("10");

        send(first, id, "11", 1);
        rejected(first, "CLIENT_BID_ID_GIA_UTILIZZATO");
        send(first, UUID.randomUUID(), "11", 1);
        rejected(first, "RILANCIO_SU_SE_STESSO");
        send(second, UUID.randomUUID(), "5000", 1);
        rejected(second, "SALDO_INSUFFICIENTE");
        send(second, UUID.randomUUID(), "11", 99);
        assertThat(rejected(second, "SEQUENCE_NON_AGGIORNATA").get("snapshotRequired")).isEqualTo(true);
        send(second, UUID.randomUUID(), "11.001", 1);
        rejected(second, "DATI_NON_VALIDI");
        send(second, UUID.randomUUID(), "11.000000000000000000001", 1);
        rejected(second, "DATI_NON_VALIDI");
        second.session().send(destination(), Map.of("type", "PLACE_BID", "clientBidId", "not-a-uuid",
                "importo", 11, "knownSequence", 1));
        rejected(second, "DATI_NON_VALIDI");
        second.session().send(destination(), "{".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        rejected(second, "DATI_NON_VALIDI");
        byte[] fractional = ("{\"type\":\"PLACE_BID\",\"clientBidId\":\""
                + UUID.randomUUID() + "\",\"importo\":11,\"knownSequence\":1.5}")
                .getBytes(java.nio.charset.StandardCharsets.UTF_8);
        assertThatThrownBy(() -> brokerConverter.fromMessage(MessageBuilder.withPayload(fractional)
                .setHeader("contentType", org.springframework.util.MimeTypeUtils.APPLICATION_JSON).build(), OffertaRequest.class))
                .isInstanceOf(org.springframework.messaging.converter.MessageConversionException.class);
        second.session().send(destination(), fractional);
        rejected(second, "DATI_NON_VALIDI");
        send(second, UUID.randomUUID(), "11", 0);
        assertThat(receive(second.privateMessages(), "BID_CONFIRMED").get("snapshotRequired")).isEqualTo(true);
        assertThat(receive(first.room(), "BID_ACCEPTED").get("sequence")).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT saldo_riservato FROM portafogli WHERE utente_id = ?",
                BigDecimal.class, primo.id())).isEqualByComparingTo("0");
    }

    @Test
    void lifecycleEventsReachRoomOnlyAfterCommit() throws Exception {
        Instant now = Instant.now();
        jdbc.update("UPDATE aste SET stato = 'PROGRAMMATA', inizio_at = ?, fine_at = ? WHERE id = ?",
                Timestamp.from(now.plusSeconds(120)), Timestamp.from(now.plusSeconds(540)), data.auctionId);
        Connection connection = connect(primo);
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            lifecycle.aggiornaStato(data.auctionId);
            status.setRollbackOnly();
        });
        assertThat(connection.room().poll(500, TimeUnit.MILLISECONDS)).isNull();
        lifecycle.aggiornaStato(data.auctionId);
        assertThat(receive(connection.room(), "ROOM_OPENED").get("sequence")).isEqualTo(1);
        now = Instant.now();
        jdbc.update("UPDATE aste SET inizio_at = ?, fine_at = ? WHERE id = ?",
                Timestamp.from(now.minusSeconds(5)), Timestamp.from(now.plusSeconds(415)), data.auctionId);
        lifecycle.aggiornaStato(data.auctionId);
        assertThat(receive(connection.room(), "AUCTION_STARTED").get("sequence")).isEqualTo(2);
        lifecycle.aggiornaStato(data.auctionId);
        assertThat(connection.room().poll(300, TimeUnit.MILLISECONDS)).isNull();
    }

    @Test
    void withdrawalSnapshotAndSettlementAreRelayedWithoutPrivateUserIds() throws Exception {
        Connection first = connect(primo);
        Connection second = connect(secondo);
        clearRooms();
        send(first, UUID.randomUUID(), "10", 0);
        receive(first.privateMessages(), "BID_CONFIRMED");
        receive(first.room(), "BID_ACCEPTED");
        send(second, UUID.randomUUID(), "11", 1);
        receive(second.privateMessages(), "BID_CONFIRMED");
        receive(first.room(), "BID_ACCEPTED");
        mvc.perform(delete("/api/v1/me").header("Authorization", secondo.bearer()))
                .andExpect(status().isNoContent());
        Map<?, ?> snapshot = receive(first.room(), "AUCTION_SNAPSHOT");
        assertThat(new BigDecimal(snapshot.get("offertaCorrente").toString())).isEqualByComparingTo("10");
        Instant now = Instant.now();
        jdbc.update("UPDATE aste SET inizio_at = ?, fine_at = ? WHERE id = ?",
                Timestamp.from(now.minusSeconds(500)), Timestamp.from(now.minusSeconds(1)), data.auctionId);
        chiusura.chiudi(data.auctionId);
        Map<?, ?> closed = receive(first.room(), "AUCTION_CLOSED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM notifiche_email WHERE asta_id = ?",
                Integer.class, data.auctionId)).isEqualTo(1);
        assertThat(closed.get("vincitoreDisplay").toString()).contains("***");
        assertThat(closed.containsKey("vincitoreId")).isFalse();
        assertThat(closed.get("sequence")).isEqualTo(4);
        assertThat(jdbc.queryForObject("SELECT quantita FROM inventario_utenti WHERE utente_id = ? AND prodotto_id = ?",
                Integer.class, primo.id(), data.productId)).isEqualTo(1);
        chiusura.chiudi(data.auctionId);
        assertThat(first.room().poll(300, TimeUnit.MILLISECONDS)).isNull();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM notifiche_email WHERE asta_id = ?",
                Integer.class, data.auctionId)).isEqualTo(1);
    }

    @Test
    void schedulerClosureReachesStompAndRealLocalSmtpOnlyOnce() throws Exception {
        Connection first = connect(primo);
        clearRooms();
        send(first, UUID.randomUUID(), "10", 0);
        receive(first.privateMessages(), "BID_CONFIRMED");
        receive(first.room(), "BID_ACCEPTED");
        expireAndRunScheduler();
        Map<?, ?> closed = receive(first.room(), "AUCTION_CLOSED");
        assertThat(closed.get("sequence")).isEqualTo(2);
        assertThat(closed.get("vincitoreDisplay").toString()).contains("***");
        assertThat(closed.containsKey("vincitoreId")).isFalse();
        assertThat(emailState()).isEqualTo("PENDING");
        sendOwnEmail();
        var message = SMTP.receive(3000);
        assertThat(message).isNotNull();
        assertThat(message.getFrom()[0].toString()).isEqualTo("aste@example.test");
        assertThat(message.getAllRecipients()[0].toString()).isEqualTo(jdbc.queryForObject(
                "SELECT email FROM utenti WHERE id=?", String.class, primo.id()));
        assertThat(message.getSubject()).contains("#" + data.auctionId);
        assertThat(message.getContent().toString()).contains("WS bid test", "10,00 CRD", "Data di conclusione:");
        assertThat(emailState()).isEqualTo("SENT");
        assertSettlementUnchanged();
        scheduler().aggiornaAste();
        assertThat(first.room().poll(300, TimeUnit.MILLISECONDS)).isNull();
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            jdbc.query("SELECT asta_id FROM notifiche_email WHERE asta_id<>? FOR UPDATE",
                    (rs, row) -> rs.getLong(1), data.auctionId);
            assertThat(delivery.inviaProssima()).isFalse();
        });
        assertThat(SMTP.receive(300)).isNull();
        assertThat(jdbc.queryForObject("SELECT tentativi FROM notifiche_email WHERE asta_id=?", Integer.class, data.auctionId))
                .isEqualTo(1);
        SMTP.assertHealthy();
    }

    @Test
    void realSmtpFailureRetriesWithoutRepeatingSettlementOrClosedEvent() throws Exception {
        Connection first = connect(primo);
        clearRooms();
        send(first, UUID.randomUUID(), "10", 0);
        receive(first.privateMessages(), "BID_CONFIRMED");
        receive(first.room(), "BID_ACCEPTED");
        expireAndRunScheduler();
        receive(first.room(), "AUCTION_CLOSED");
        SMTP.failNextDelivery();
        sendOwnEmail();
        assertThat(emailState()).isEqualTo("PENDING");
        assertThat(SMTP.receive(100)).isNull();
        assertThat(jdbc.queryForObject("SELECT ultimo_errore FROM notifiche_email WHERE asta_id=?", String.class, data.auctionId))
                .isEqualTo("SMTP_ERROR");
        assertThat(jdbc.queryForObject("SELECT prossimo_tentativo_at > CURRENT_TIMESTAMP FROM notifiche_email WHERE asta_id=?",
                Boolean.class, data.auctionId)).isTrue();
        assertSettlementUnchanged();
        jdbc.update("UPDATE notifiche_email SET prossimo_tentativo_at=CURRENT_TIMESTAMP - INTERVAL '1 day' WHERE asta_id=?", data.auctionId);
        sendOwnEmail();
        assertThat(SMTP.receive(3000)).isNotNull();
        assertThat(emailState()).isEqualTo("SENT");
        assertThat(jdbc.queryForObject("SELECT tentativi FROM notifiche_email WHERE asta_id=?", Integer.class, data.auctionId))
                .isEqualTo(2);
        assertSettlementUnchanged();
        assertThat(first.room().poll(300, TimeUnit.MILLISECONDS)).isNull();
        SMTP.assertHealthy();
    }

    @Test
    void schedulerWithoutWinnerPublishesClosureButQueuesNoEmail() throws Exception {
        Connection first = connect(primo);
        clearRooms();
        expireAndRunScheduler();
        Map<?, ?> closed = receive(first.room(), "AUCTION_CLOSED");
        assertThat(closed.get("vincitoreDisplay")).isNull();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notifiche_email WHERE asta_id=?", Integer.class, data.auctionId)).isZero();
        assertThat(jdbc.queryForObject("SELECT quantita_disponibile FROM prodotti WHERE id=?", Integer.class, data.productId)).isEqualTo(1);
        assertThat(SMTP.receive(100)).isNull();
    }

    @Test
    void requiredStartTlsDoesNotSilentlySendToPlaintextServer() throws Exception {
        Connection first = connect(primo);
        clearRooms();
        send(first, UUID.randomUUID(), "10", 0);
        receive(first.privateMessages(), "BID_CONFIRMED");
        receive(first.room(), "BID_ACCEPTED");
        expireAndRunScheduler();
        receive(first.room(), "AUCTION_CLOSED");
        var properties = mail.getJavaMailProperties();
        properties.setProperty("mail.smtp.starttls.enable", "true");
        properties.setProperty("mail.smtp.starttls.required", "true");
        try {
            sendOwnEmail();
            assertThat(emailState()).isEqualTo("PENDING");
            assertThat(SMTP.receive(100)).isNull();
            assertSettlementUnchanged();
        } finally {
            properties.setProperty("mail.smtp.starttls.enable", "false");
            properties.setProperty("mail.smtp.starttls.required", "false");
        }
    }

    private AstaScheduler scheduler() { return new AstaScheduler(aste, lifecycle, chiusura); }
    private void expireAndRunScheduler() {
        Instant now = Instant.now();
        jdbc.update("UPDATE aste SET inizio_at=?, fine_at=? WHERE id=?",
                Timestamp.from(now.minusSeconds(500)), Timestamp.from(now.minusSeconds(1)), data.auctionId);
        scheduler().aggiornaAste();
    }

    private String emailState() {
        return jdbc.queryForObject("SELECT stato FROM notifiche_email WHERE asta_id=?", String.class, data.auctionId);
    }

    private void sendOwnEmail() {
        // Il worker usa SKIP LOCKED: bloccare le altre richieste evita invii delle fixture altrui.
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            jdbc.query("SELECT asta_id FROM notifiche_email WHERE asta_id<>? FOR UPDATE",
                    (rs, row) -> rs.getLong(1), data.auctionId);
            assertThat(delivery.inviaProssima()).isTrue();
        });
    }

    private void assertSettlementUnchanged() {
        assertThat(jdbc.queryForObject("SELECT stato FROM aste WHERE id=?", String.class, data.auctionId)).isEqualTo("CHIUSA");
        assertThat(jdbc.queryForObject("SELECT saldo_totale FROM portafogli WHERE utente_id=?", BigDecimal.class, primo.id())).isEqualByComparingTo("990");
        assertThat(jdbc.queryForObject("SELECT saldo_riservato FROM portafogli WHERE utente_id=?", BigDecimal.class, primo.id())).isZero();
        assertThat(jdbc.queryForObject("SELECT saldo_totale FROM portafogli WHERE utente_id=?", BigDecimal.class, admin.id())).isEqualByComparingTo("10");
        assertThat(jdbc.queryForObject("SELECT quantita FROM inventario_utenti WHERE utente_id=? AND prodotto_id=?",
                Integer.class, primo.id(), data.productId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM movimenti_portafoglio WHERE asta_id=? AND tipo IN ('PAGAMENTO_ASTA','INCASSO_ASTA')",
                Integer.class, data.auctionId)).isEqualTo(2);
    }

    private Account account() throws Exception {
        String username = "wb" + UUID.randomUUID().toString().substring(0, 12);
        data.usernames.add(username);
        mvc.perform(post("/api/v1/auth/register").contentType("application/json")
                .content(json.writeValueAsString(Map.of("username", username,
                        "email", username + "@example.com", "password", "TestPassword123!"))))
                .andExpect(status().isCreated());
        JsonNode login = json.readTree(mvc.perform(post("/api/v1/auth/login").contentType("application/json")
                .content(json.writeValueAsString(Map.of("username", username, "password", "TestPassword123!"))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        long id = login.path("userId").asLong();
        data.userIds.add(id);
        return new Account(id, "Bearer " + login.path("accessToken").asText());
    }

    private Connection connect(Account account) throws Exception {
        JsonNode ticket = json.readTree(mvc.perform(post("/api/v1/aste/" + data.auctionId + "/ticket")
                .header("Authorization", account.bearer())).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        var client = new WebSocketStompClient(new StandardWebSocketClient());
        client.setMessageConverter(new MappingJackson2MessageConverter() {
            @Override
            protected Object convertToInternal(Object payload, MessageHeaders headers, Object hint) {
                // Consente al test di inviare anche JSON malformato, senza serializzarlo come base64.
                return payload instanceof byte[] ? payload : super.convertToInternal(payload, headers, hint);
            }
        });
        WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
        headers.setOrigin("http://localhost:8082");
        StompSession session;
        try {
            session = client.connectAsync("ws://localhost:" + port + "/ws?ticket=" + ticket.path("ticket").asText(),
                    headers, new StompSessionHandlerAdapter() {}).get(10, TimeUnit.SECONDS);
        } catch (Exception exception) {
            client.stop();
            throw exception;
        }
        Connection connection = new Connection(client, session, new LinkedBlockingQueue<>(), new LinkedBlockingQueue<>());
        connections.add(connection);
        session.subscribe("/user/queue/aste", frames(connection.privateMessages()));
        session.subscribe("/topic/aste/" + data.auctionId, frames(connection.room()));
        session.send("/app/aste/" + data.auctionId + "/join", Map.of());
        receive(connection.privateMessages(), "PRESENCE_SNAPSHOT");
        // Il join è una barriera sul canale ordinato: le sottoscrizioni precedenti sono già elaborate.
        connection.room().clear();
        return connection;
    }

    private void clearRooms() { connections.forEach(c -> c.room().clear()); }
    private String destination() { return "/app/aste/" + data.auctionId + "/offerte"; }
    private void send(Connection connection, UUID id, String amount, long sequence) {
        connection.session().send(destination(), Map.of("type", "PLACE_BID", "clientBidId", id.toString(),
                "importo", new BigDecimal(amount), "knownSequence", sequence));
    }

    private static Map<?, ?> rejected(Connection connection, String code) throws Exception {
        var result = connection.privateMessages().poll(10, TimeUnit.SECONDS);
        assertThat(result).isNotNull();
        assertThat(result.get("type")).isEqualTo("BID_REJECTED");
        assertThat(result.get("code")).isEqualTo(code);
        return result;
    }

    private static Map<?, ?> receive(BlockingQueue<Map<?, ?>> messages, String type) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            Map<?, ?> message = messages.poll(deadline - System.nanoTime(), TimeUnit.NANOSECONDS);
            assertThat(message).as("Messaggio %s", type).isNotNull();
            if (type.equals(message.get("type"))) return message;
        }
        throw new AssertionError("Messaggio non ricevuto: " + type);
    }

    private static StompFrameHandler frames(BlockingQueue<Map<?, ?>> messages) {
        return new StompFrameHandler() {
            public Type getPayloadType(StompHeaders headers) { return Map.class; }
            public void handleFrame(StompHeaders headers, Object payload) { messages.offer((Map<?, ?>) payload); }
        };
    }

    private record Account(long id, String bearer) {}
    private record Connection(WebSocketStompClient client, StompSession session,
                              BlockingQueue<Map<?, ?>> privateMessages, BlockingQueue<Map<?, ?>> room) {}
}
