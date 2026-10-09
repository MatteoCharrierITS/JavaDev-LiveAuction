package it.esercitazione.liveauction.producer.portafoglio;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.esercitazione.liveauction.producer.asta.requests.OffertaRequest;
import it.esercitazione.liveauction.producer.asta.services.ChiusuraAstaService;
import it.esercitazione.liveauction.producer.asta.services.OffertaService;
import it.esercitazione.liveauction.producer.portafoglio.requests.ImpostaSaldoRequest;
import it.esercitazione.liveauction.producer.portafoglio.services.PortafoglioService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"spring.docker.compose.enabled=false", "app.aste.scheduler.enabled=false",
        "app.notifiche.email.enabled=false"})
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "RUN_DB_TESTS", matches = "true")
class PortafoglioIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtDecoder decoder;
    @Autowired PortafoglioService service;
    @Autowired OffertaService offerte;
    @Autowired ChiusuraAstaService chiusura;
    @Autowired PlatformTransactionManager transactions;
    private final List<Long> utenti = new ArrayList<>();
    private Account user;
    private Long asta;
    private Long prodotto;
    private Long categoria;

    @BeforeEach
    void prepara() throws Exception {
        user = account("USER");
    }

    @AfterEach
    void pulisci() {
        SecurityContextHolder.clearContext();
        if (asta != null) {
            jdbc.update("DELETE FROM notifiche_email WHERE asta_id = ?", asta);
            jdbc.update("DELETE FROM movimenti_portafoglio WHERE asta_id = ?", asta);
            jdbc.update("DELETE FROM offerte WHERE asta_id = ?", asta);
            jdbc.update("DELETE FROM aste WHERE id = ?", asta);
        }
        if (prodotto != null) {
            jdbc.update("DELETE FROM inventario_utenti WHERE prodotto_id = ?", prodotto);
            jdbc.update("DELETE FROM prodotti WHERE id = ?", prodotto);
            jdbc.update("DELETE FROM categorie WHERE id = ?", categoria);
        }
        for (long id : utenti) {
            jdbc.update("DELETE FROM movimenti_portafoglio WHERE portafoglio_id IN (SELECT id FROM portafogli WHERE utente_id = ?)", id);
            jdbc.update("DELETE FROM portafogli WHERE utente_id = ?", id);
            jdbc.update("DELETE FROM auth_sessions WHERE utente_id = ?", id);
            jdbc.update("DELETE FROM utenti WHERE id = ?", id);
        }
    }

    @Test
    void startsAtZeroAndReturnsOnlyOwnPaginatedLedger() throws Exception {
        mvc.perform(get("/api/v1/me/portafoglio").header("Authorization", user.bearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.saldoTotale").value(0))
                .andExpect(jsonPath("$.saldoDisponibile").value(0))
                .andExpect(jsonPath("$.valuta").value("CRD"))
                .andExpect(jsonPath("$.movimenti").isEmpty()).andExpect(jsonPath("$.totalPages").value(0));
        imposta(user, "100");
        imposta(user, "75");
        imposta(user, "90");
        Account altro = account("USER");
        imposta(altro, "999");
        // A parità di data, l'ID garantisce una paginazione stabile.
        jdbc.update("UPDATE movimenti_portafoglio SET data_movimento = ? WHERE portafoglio_id = (SELECT id FROM portafogli WHERE utente_id = ?)",
                Timestamp.from(Instant.parse("2026-10-09T07:00:00Z")), user.id());
        mvc.perform(get("/api/v1/me/portafoglio?page=0&size=2").header("Authorization", user.bearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.saldoTotale").value(90))
                .andExpect(jsonPath("$.totalElements").value(3)).andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.movimenti.length()").value(2))
                .andExpect(jsonPath("$.movimenti[0].importo").value(15))
                .andExpect(jsonPath("$.movimenti[1].importo").value(25))
                .andExpect(jsonPath("$.movimenti[0].dataMovimento").value("2026-10-09T07:00:00Z"))
                .andExpect(jsonPath("$.movimenti[0].astaId").isEmpty());
        mvc.perform(get("/api/v1/me/portafoglio?page=1&size=2").header("Authorization", user.bearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.movimenti.length()").value(1))
                .andExpect(jsonPath("$.movimenti[0].importo").value(100));
    }

    @Test
    void absoluteSettingsRecordDeltasAndIdenticalPutIsNoOp() throws Exception {
        imposta(user, "100");
        imposta(user, "100.00");
        assertThat(versione()).isEqualTo(1);
        assertThat(count()).isEqualTo(1);
        imposta(user, "40");
        imposta(user, "0");
        assertThat(versione()).isEqualTo(3);
        assertThat(jdbc.queryForList("SELECT importo FROM movimenti_portafoglio WHERE portafoglio_id = (SELECT id FROM portafogli WHERE utente_id = ?) ORDER BY id", BigDecimal.class, user.id()))
                .containsExactly(new BigDecimal("100.00"), new BigDecimal("60.00"), new BigDecimal("40.00"));
        assertThat(jdbc.queryForObject("SELECT saldo_totale_dopo FROM movimenti_portafoglio WHERE portafoglio_id = (SELECT id FROM portafogli WHERE utente_id = ?) ORDER BY id DESC LIMIT 1", BigDecimal.class, user.id())).isEqualByComparingTo("0");
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"saldoTotale\":null}", "{\"saldoTotale\":-1}",
            "{\"saldoTotale\":10000000000}", "{\"saldoTotale\":1.001}", "{\"saldoTotale\":\"abc\"}"})
    void rejectsInvalidSettingsWithoutEffects(String body) throws Exception {
        mvc.perform(put("/api/v1/me/portafoglio/impostazioni").header("Authorization", user.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest()).andExpect(content().contentTypeCompatibleWith("application/problem+json"));
        assertThat(versione()).isZero();
        assertThat(count()).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"page=-1", "size=0", "size=101", "page=abc"})
    void rejectsInvalidPagination(String query) throws Exception {
        mvc.perform(get("/api/v1/me/portafoglio?" + query).header("Authorization", user.bearer()))
                .andExpect(status().isBadRequest()).andExpect(content().contentTypeCompatibleWith("application/problem+json"));
    }

    @Test
    void enforcesRoleIdentityAndRevocationOnBothRoutes() throws Exception {
        Account admin = account("ADMIN");
        for (String method : List.of("GET", "PUT")) {
            var request = method.equals("GET") ? get("/api/v1/me/portafoglio")
                    : put("/api/v1/me/portafoglio/impostazioni").contentType(MediaType.APPLICATION_JSON).content("{\"saldoTotale\":10}");
            mvc.perform(request).andExpect(status().isUnauthorized());
            mvc.perform(request.header("Authorization", admin.bearer())).andExpect(status().isForbidden());
        }
        assertThatThrownBy(() -> autenticato(user, () -> service.leggi(admin.id(), 0, 20)))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> autenticato(user, () -> service.imposta(admin.id(), new ImpostaSaldoRequest(BigDecimal.TEN))))
                .isInstanceOf(AccessDeniedException.class);
        mvc.perform(post("/api/v1/auth/logout").header("Authorization", user.bearer())).andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/me/portafoglio").header("Authorization", user.bearer())).andExpect(status().isUnauthorized());
        mvc.perform(put("/api/v1/me/portafoglio/impostazioni").header("Authorization", user.bearer())
                .contentType(MediaType.APPLICATION_JSON).content("{\"saldoTotale\":10}")).andExpect(status().isUnauthorized());
    }

    @Test
    void rollbackRevertsBothBalanceAndLedger() {
        autenticato(user, () -> new TransactionTemplate(transactions).execute(status -> {
            service.imposta(user.id(), new ImpostaSaldoRequest(new BigDecimal("123")));
            status.setRollbackOnly();
            return null;
        }));
        assertThat(versione()).isZero();
        assertThat(count()).isZero();
        assertThat(totale()).isEqualByComparingTo("0");
    }

    @Test
    void concurrentSettingsHaveConsistentLedgerAndVersion() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<?> first = pool.submit(() -> { await(start); return autenticato(user,
                    () -> service.imposta(user.id(), new ImpostaSaldoRequest(new BigDecimal("100")))); });
            Future<?> second = pool.submit(() -> { await(start); return autenticato(user,
                    () -> service.imposta(user.id(), new ImpostaSaldoRequest(new BigDecimal("200")))); });
            start.countDown();
            first.get(10, TimeUnit.SECONDS);
            second.get(10, TimeUnit.SECONDS);
            assertThat(count()).isEqualTo(2);
            assertThat(versione()).isEqualTo(2);
            var rows = jdbc.queryForList("SELECT importo, saldo_totale_dopo FROM movimenti_portafoglio WHERE portafoglio_id = (SELECT id FROM portafogli WHERE utente_id = ?) ORDER BY id", user.id());
            BigDecimal firstTotal = (BigDecimal) rows.getFirst().get("saldo_totale_dopo");
            assertThat((BigDecimal) rows.get(1).get("importo")).isEqualByComparingTo(totale().subtract(firstTotal).abs());
            assertThat((BigDecimal) rows.get(1).get("saldo_totale_dopo")).isEqualByComparingTo(totale());
        } finally { pool.shutdownNow(); }
    }

    @Test
    void integratesWithBidsReleaseAndSettlementWithoutDuplicatingLedger() throws Exception {
        Account admin = account("ADMIN");
        Account altro = account("USER");
        creaAsta(admin);
        imposta(user, "1000");
        imposta(altro, "1000");
        autenticato(user, () -> offerte.piazza(user.id(), asta, new OffertaRequest("PLACE_BID", UUID.randomUUID(), new BigDecimal("100"), 0L)));
        mvc.perform(put("/api/v1/me/portafoglio/impostazioni").header("Authorization", user.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"saldoTotale\":99}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SALDO_INFERIORE_AL_RISERVATO"))
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
        assertThat(count()).isEqualTo(2);
        imposta(user, "100");
        mvc.perform(get("/api/v1/me/portafoglio").header("Authorization", user.bearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.saldoRiservato").value(100))
                .andExpect(jsonPath("$.saldoDisponibile").value(0));
        autenticato(altro, () -> offerte.piazza(altro.id(), asta, new OffertaRequest("PLACE_BID", UUID.randomUUID(), new BigDecimal("101"), 1L)));
        assertThat(jdbc.queryForObject("SELECT saldo_riservato FROM portafogli WHERE utente_id = ?", BigDecimal.class, user.id())).isEqualByComparingTo("0");
        jdbc.update("UPDATE aste SET fine_at = ? WHERE id = ?", Timestamp.from(Instant.now().minusSeconds(1)), asta);
        chiusura.chiudi(asta);
        chiusura.chiudi(asta);
        assertThat(jdbc.queryForObject("SELECT saldo_totale FROM portafogli WHERE utente_id = ?", BigDecimal.class, altro.id())).isEqualByComparingTo("899");
        assertThat(jdbc.queryForObject("SELECT saldo_totale FROM portafogli WHERE utente_id = ?", BigDecimal.class, admin.id())).isEqualByComparingTo("101");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM movimenti_portafoglio WHERE asta_id = ? AND tipo IN ('PAGAMENTO_ASTA','INCASSO_ASTA')", Long.class, asta)).isEqualTo(2);
    }

    @Test
    void settingsWaitForConcurrentBidAndCheckCommittedReservation() throws Exception {
        creaAsta(account("ADMIN"));
        imposta(user, "1000");
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch reserved = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch settingStarted = new CountDownLatch(1);
        try {
            Future<?> bid = pool.submit(() -> autenticato(user,
                    () -> new TransactionTemplate(transactions).execute(status -> {
                        offerte.piazza(user.id(), asta, new OffertaRequest("PLACE_BID", UUID.randomUUID(), new BigDecimal("100"), 0L));
                        reserved.countDown();
                        await(release);
                        return null;
                    })));
            assertThat(reserved.await(10, TimeUnit.SECONDS)).isTrue();
            Future<?> setting = pool.submit(() -> {
                settingStarted.countDown();
                return autenticato(user, () -> service.imposta(user.id(), new ImpostaSaldoRequest(new BigDecimal("99"))));
            });
            assertThat(settingStarted.await(10, TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> setting.get(200, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
            release.countDown();
            bid.get(10, TimeUnit.SECONDS);
            assertThatThrownBy(() -> setting.get(10, TimeUnit.SECONDS))
                    .isInstanceOf(ExecutionException.class)
                    .hasCauseInstanceOf(it.esercitazione.liveauction.producer.portafoglio.exceptions.PortafoglioException.class);
            assertThat(totale()).isEqualByComparingTo("1000");
            assertThat(count()).isEqualTo(2);
            assertThat(jdbc.queryForObject("SELECT saldo_riservato FROM portafogli WHERE utente_id = ?", BigDecimal.class, user.id()))
                    .isEqualByComparingTo("100");
        } finally {
            release.countDown();
            pool.shutdownNow();
            assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void missingWalletReturnsProblemDetail() throws Exception {
        jdbc.update("DELETE FROM portafogli WHERE utente_id = ?", user.id());
        mvc.perform(get("/api/v1/me/portafoglio").header("Authorization", user.bearer()))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("RISORSA_NON_TROVATA"))
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
    }

    private Account account(String role) throws Exception {
        String username = "wallet_" + UUID.randomUUID().toString().substring(0, 12);
        String body = "{\"username\":\"" + username + "\",\"email\":\"" + username + "@example.com\",\"password\":\"TestPassword123!\"}";
        long id = json.readTree(mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asLong();
        utenti.add(id);
        if (role.equals("ADMIN")) jdbc.update("UPDATE utenti SET ruolo = 'ADMIN' WHERE id = ?", id);
        String token = json.readTree(mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"TestPassword123!\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("accessToken").asText();
        return new Account(id, token);
    }

    private void imposta(Account account, String total) throws Exception {
        mvc.perform(put("/api/v1/me/portafoglio/impostazioni").header("Authorization", account.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"saldoTotale\":" + total + "}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.saldoTotale").value(new BigDecimal(total).doubleValue()));
    }

    private void creaAsta(Account admin) {
        String suffix = UUID.randomUUID().toString();
        categoria = jdbc.queryForObject("INSERT INTO categorie(nome,slug) VALUES (?,?) RETURNING id", Long.class, suffix, suffix);
        prodotto = jdbc.queryForObject("INSERT INTO prodotti(categoria_id,sku,nome,astabile,quantita_bloccata) VALUES (?,?,'Test wallet',TRUE,1) RETURNING id", Long.class, categoria, suffix.substring(0, 20));
        asta = jdbc.queryForObject("INSERT INTO aste(prodotto_id,admin_id,stato,prezzo_iniziale,inizio_at,fine_at) VALUES (?,?,'APERTA',100,?,?) RETURNING id", Long.class,
                prodotto, admin.id(), Timestamp.from(Instant.now().minusSeconds(3600)), Timestamp.from(Instant.now().plusSeconds(300)));
    }

    private long versione() { return jdbc.queryForObject("SELECT versione FROM portafogli WHERE utente_id = ?", Long.class, user.id()); }
    private long count() { return jdbc.queryForObject("SELECT COUNT(*) FROM movimenti_portafoglio WHERE portafoglio_id = (SELECT id FROM portafogli WHERE utente_id = ?)", Long.class, user.id()); }
    private BigDecimal totale() { return jdbc.queryForObject("SELECT saldo_totale FROM portafogli WHERE utente_id = ?", BigDecimal.class, user.id()); }
    private <T> T autenticato(Account account, Supplier<T> work) {
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new JwtAuthenticationToken(decoder.decode(account.token()), List.of(new SimpleGrantedAuthority("ROLE_USER"))));
        SecurityContextHolder.setContext(context);
        try { return work.get(); } finally { SecurityContextHolder.clearContext(); }
    }
    private static void await(CountDownLatch latch) {
        try { if (!latch.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Timeout"); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
    }
    private record Account(long id, String token) { String bearer() { return "Bearer " + token; } }
}
