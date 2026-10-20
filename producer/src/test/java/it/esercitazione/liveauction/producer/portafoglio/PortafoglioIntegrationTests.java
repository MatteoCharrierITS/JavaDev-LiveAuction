package it.esercitazione.liveauction.producer.portafoglio;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.esercitazione.liveauction.producer.portafoglio.services.PortafoglioService;
import it.esercitazione.liveauction.producer.portafoglio.repos.PortafoglioRepository;
import it.esercitazione.liveauction.producer.portafoglio.model.TipoMovimento;
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
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
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
    @Autowired PortafoglioRepository repository;
    @Autowired PlatformTransactionManager transactions;
    private final List<Long> utenti = new ArrayList<>();
    private Account user;

    @BeforeEach void prepara() throws Exception { user = account("USER"); }

    @AfterEach void pulisci() {
        SecurityContextHolder.clearContext();
        for (long id : utenti) {
            jdbc.update("DELETE FROM movimenti_portafoglio WHERE portafoglio_id IN (SELECT id FROM portafogli WHERE utente_id = ?)", id);
            jdbc.update("DELETE FROM portafogli WHERE utente_id = ?", id);
            jdbc.update("DELETE FROM auth_sessions WHERE utente_id = ?", id);
            jdbc.update("DELETE FROM utenti WHERE id = ?", id);
        }
    }

    @Test void startsWithConfiguredWelcomeCreditAndPrivateLedger() throws Exception {
        account("USER");
        mvc.perform(get("/api/v1/me/portafoglio").header("Authorization", user.bearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.saldoTotale").value(10000))
                .andExpect(jsonPath("$.saldoDisponibile").value(10000))
                .andExpect(jsonPath("$.saldoRiservato").value(0))
                .andExpect(jsonPath("$.movimenti.length()").value(1))
                .andExpect(jsonPath("$.movimenti[0].tipo").value("CREDITO_INIZIALE"))
                .andExpect(jsonPath("$.movimenti[0].importo").value(10000))
                .andExpect(jsonPath("$.movimenti[0].astaId").isEmpty())
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"saldoTotale\":9999999999.99}", "{\"saldoTotale\":0}"})
    void balanceCannotBeChangedThroughOldApi(String body) throws Exception {
        mvc.perform(put("/api/v1/me/portafoglio/impostazioni").header("Authorization", user.bearer())
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
        assertThat(totale()).isEqualByComparingTo("10000");
        assertThat(count()).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"page=-1", "size=0", "size=101", "page=abc"})
    void rejectsInvalidPagination(String query) throws Exception {
        mvc.perform(get("/api/v1/me/portafoglio?" + query).header("Authorization", user.bearer()))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
    }

    @Test void roleIdentityAndRevocationStillProtectReadOnlyWallet() throws Exception {
        Account admin = account("ADMIN");
        mvc.perform(get("/api/v1/me/portafoglio")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/me/portafoglio").header("Authorization", admin.bearer()))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/v1/me/portafoglio/impostazioni").header("Authorization", admin.bearer())
                .contentType(MediaType.APPLICATION_JSON).content("{\"saldoTotale\":1}"))
                .andExpect(status().isForbidden());
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new JwtAuthenticationToken(decoder.decode(user.token()),
                List.of(new SimpleGrantedAuthority("ROLE_USER"))));
        SecurityContextHolder.setContext(context);
        try {
            assertThatThrownBy(() -> service.leggi(admin.id(), 0, 20)).isInstanceOf(AccessDeniedException.class);
        } finally { SecurityContextHolder.clearContext(); }
        mvc.perform(post("/api/v1/auth/logout").header("Authorization", user.bearer())).andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/me/portafoglio").header("Authorization", user.bearer())).andExpect(status().isUnauthorized());
    }

    @Test void internalMovementsRemainAtomicAndLedgerIsPaginated() throws Exception {
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            var saldo = repository.bloccaPortafogli(List.of(user.id())).get(user.id());
            repository.movimenta(saldo, null, TipoMovimento.RISERVA_OFFERTA, BigDecimal.TEN,
                    BigDecimal.ZERO, BigDecimal.TEN, Instant.now());
            status.setRollbackOnly();
        });
        assertThat(count()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT saldo_riservato FROM portafogli WHERE utente_id = ?",
                BigDecimal.class, user.id())).isEqualByComparingTo("0");
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            var saldo = repository.bloccaPortafogli(List.of(user.id())).get(user.id());
            saldo = repository.movimenta(saldo, null, TipoMovimento.RISERVA_OFFERTA, BigDecimal.TEN,
                    BigDecimal.ZERO, BigDecimal.TEN, Instant.now());
            repository.movimenta(saldo, null, TipoMovimento.RILASCIO_OFFERTA, BigDecimal.TEN,
                    BigDecimal.ZERO, BigDecimal.TEN.negate(), Instant.now());
        });
        mvc.perform(get("/api/v1/me/portafoglio?page=0&size=2").header("Authorization", user.bearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.movimenti.length()").value(2))
                .andExpect(jsonPath("$.movimenti[0].tipo").value("RILASCIO_OFFERTA"))
                .andExpect(jsonPath("$.totalElements").value(3)).andExpect(jsonPath("$.totalPages").value(2));
        mvc.perform(get("/api/v1/me/portafoglio?page=1&size=2").header("Authorization", user.bearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.movimenti[0].tipo").value("CREDITO_INIZIALE"));
    }

    @Test void creatingWalletAgainCannotCreditItAgain() {
        assertThatThrownBy(() -> new TransactionTemplate(transactions).executeWithoutResult(status ->
                repository.creaConCreditoIniziale(user.id(), new BigDecimal("10000"))))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThat(count()).isEqualTo(1);
        assertThat(totale()).isEqualByComparingTo("10000");
    }

    @Test void missingWalletReturnsProblemDetail() throws Exception {
        jdbc.update("DELETE FROM movimenti_portafoglio WHERE portafoglio_id IN (SELECT id FROM portafogli WHERE utente_id = ?)", user.id());
        jdbc.update("DELETE FROM portafogli WHERE utente_id = ?", user.id());
        mvc.perform(get("/api/v1/me/portafoglio").header("Authorization", user.bearer()))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("RISORSA_NON_TROVATA"));
    }

    private Account account(String role) throws Exception {
        String username = "wallet_" + UUID.randomUUID().toString().substring(0, 12);
        String body = "{\"username\":\"" + username + "\",\"email\":\"" + username
                + "@example.com\",\"password\":\"TestPassword123!\"}";
        long id = json.readTree(mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asLong();
        utenti.add(id);
        if (role.equals("ADMIN")) jdbc.update("UPDATE utenti SET ruolo = 'ADMIN' WHERE id = ?", id);
        String token = json.readTree(mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + username + "\",\"password\":\"TestPassword123!\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("accessToken").asText();
        return new Account(id, token);
    }
    private long count() { return jdbc.queryForObject("SELECT COUNT(*) FROM movimenti_portafoglio WHERE portafoglio_id = (SELECT id FROM portafogli WHERE utente_id = ?)", Long.class, user.id()); }
    private BigDecimal totale() { return jdbc.queryForObject("SELECT saldo_totale FROM portafogli WHERE utente_id = ?", BigDecimal.class, user.id()); }
    private record Account(long id, String token) { String bearer() { return "Bearer " + token; } }
}
