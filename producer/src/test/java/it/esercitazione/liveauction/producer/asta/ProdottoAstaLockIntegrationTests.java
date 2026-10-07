package it.esercitazione.liveauction.producer.asta;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import it.esercitazione.liveauction.producer.asta.repos.ProdottoAstaRepository;
import it.esercitazione.liveauction.producer.asta.exceptions.AstaException;
import it.esercitazione.liveauction.producer.asta.responses.ProgrammaAstaResponse;
import it.esercitazione.liveauction.producer.asta.models.Stato;
import it.esercitazione.liveauction.producer.asta.requests.ProgrammaAstaRequest;
import it.esercitazione.liveauction.producer.asta.services.AstaService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.docker.compose.enabled=false",
        "app.aste.scheduler.enabled=false",
        "spring.datasource.url=${DB_URL}"
})
@EnabledIfEnvironmentVariable(named = "RUN_DB_TESTS", matches = "true")
@EnabledIfEnvironmentVariable(named = "DB_URL", matches = "jdbc:postgresql:.*")
@AutoConfigureMockMvc
class ProdottoAstaLockIntegrationTests {

    private static final String PASSWORD = "AuctionTest123!";

    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    ProdottoAstaRepository repository;
    @Autowired
    AstaService service;
    @Autowired
    PlatformTransactionManager transactionManager;
    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper json;
    @Autowired
    PasswordEncoder passwordEncoder;

    private Long categoriaId;
    private Long prodottoId;
    private Long adminId;

    @BeforeEach
    void preparaProdottoIsolato() {
        String suffisso = UUID.randomUUID().toString().substring(0, 8);
        categoriaId = jdbc.queryForObject(
                "INSERT INTO categorie (nome, slug) VALUES (?, ?) RETURNING id",
                Long.class, "Test lock " + suffisso, "test-lock-" + suffisso);
        prodottoId = jdbc.queryForObject("""
                INSERT INTO prodotti (categoria_id, sku, nome, astabile, quantita_disponibile)
                VALUES (?, ?, ?, TRUE, 1) RETURNING id
                """, Long.class, categoriaId, "LOCK-" + suffisso, "Prodotto test lock");
        adminId = jdbc.queryForObject("""
                INSERT INTO utenti (username, email, password_hash, ruolo, attivo)
                VALUES (?, ?, ?, 'ADMIN', TRUE) RETURNING id
                """, Long.class, "admin_lock_" + suffisso, "admin_lock_" + suffisso + "@example.invalid",
                passwordEncoder.encode(PASSWORD));
    }

    @AfterEach
    void eliminaSoloIRecordDelTest() {
        SecurityContextHolder.clearContext();
        if (prodottoId != null) {
            jdbc.update("DELETE FROM aste WHERE prodotto_id = ?", prodottoId);
            jdbc.update("DELETE FROM prodotti WHERE id = ?", prodottoId);
        }
        if (categoriaId != null) {
            jdbc.update("DELETE FROM categorie WHERE id = ?", categoriaId);
        }
        if (adminId != null) {
            jdbc.update("DELETE FROM auth_sessions WHERE utente_id = ?", adminId);
            jdbc.update("DELETE FROM utenti WHERE id = ?", adminId);
        }
    }

    @Test
    void restCreaAstaConLocationEDtoSenzaDatiUtente() throws Exception {
        Map<String, Object> body = bodyRichiesta();
        body.put("adminId", adminId + 1000); // Il creatore resta quello del token.
        var risultato = mvc.perform(post("/api/v1/admin/aste")
                        .header("Authorization", "Bearer " + token())
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", matchesPattern("/api/v1/aste/\\d+")))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.stato").value("PROGRAMMATA"))
                .andExpect(jsonPath("$.prodotto.id").value(prodottoId))
                .andExpect(jsonPath("$.prodotto.nome").value("Prodotto test lock"))
                .andExpect(jsonPath("$.prezzoIniziale").value(500.00))
                .andExpect(jsonPath("$.incrementoMinimo").value(1.00))
                .andExpect(jsonPath("$.sequence").value(0))
                .andReturn();

        JsonNode risposta = json.readTree(risultato.getResponse().getContentAsString());
        assertThat(risultato.getResponse().getHeader("Location"))
                .isEqualTo("/api/v1/aste/" + risposta.get("id").asLong());
        List<String> campi = new ArrayList<>();
        risposta.fieldNames().forEachRemaining(campi::add);
        assertThat(campi).containsExactlyInAnyOrder("id", "stato", "prodotto", "prezzoIniziale",
                "incrementoMinimo", "aperturaStanzaAt", "inizioAt", "fineAt", "serverTime", "sequence");
        assertThat(risposta.get("prodotto").size()).isEqualTo(2);
        Instant inizioAt = richiesta().inizioLocale().atZone(ZoneId.of("Europe/Rome")).toInstant();
        assertThat(risposta.get("inizioAt").asText()).isEqualTo(inizioAt.toString());
        assertThat(risposta.get("aperturaStanzaAt").asText()).isEqualTo(inizioAt.minusSeconds(180).toString());
        assertThat(risposta.get("fineAt").asText()).isEqualTo(inizioAt.plusSeconds(420).toString());
        assertThat(risposta.get("serverTime").asText()).endsWith("Z");
        assertThat(jdbc.queryForObject("SELECT admin_id FROM aste WHERE id = ?", Long.class,
                risposta.get("id").asLong())).isEqualTo(adminId);
        assertStock(0, 1);
    }

    @Test
    void restRichiedeAutenticazione() throws Exception {
        mvc.perform(post("/api/v1/admin/aste").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(bodyRichiesta())))
                .andExpect(status().isUnauthorized());
        assertNessunaPrenotazione();
    }

    @Test
    void restUserRiceve403() throws Exception {
        jdbc.update("UPDATE utenti SET ruolo = 'USER' WHERE id = ?", adminId);
        mvc.perform(post("/api/v1/admin/aste").header("Authorization", "Bearer " + token())
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(bodyRichiesta())))
                .andExpect(status().isForbidden());
        assertNessunaPrenotazione();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"0", "-1", "0.001", "10000000000", "non-numerico"})
    void restPrezzoNonValidoRestituisce422(String prezzo) throws Exception {
        Map<String, Object> body = bodyRichiesta();
        body.put("prezzoIniziale", prezzo);
        assertErroreRest(body, 422, "PREZZO_INIZIALE_NON_VALIDO");
        assertNessunaPrenotazione();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"non-una-data", "2026-02-30T12:00:00", "2000-01-01T12:00:00"})
    void restDataNonValidaRestituisce422(String data) throws Exception {
        Map<String, Object> body = bodyRichiesta();
        body.put("inizioLocale", data);
        assertErroreRest(body, 422, "DATA_INIZIO_NON_VALIDA");
        assertNessunaPrenotazione();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "UTC", "Europe/Paris"})
    void restFusoNonValidoRestituisce422(String fuso) throws Exception {
        Map<String, Object> body = bodyRichiesta();
        body.put("timeZone", fuso);
        assertErroreRest(body, 422, "DATA_INIZIO_NON_VALIDA");
        assertNessunaPrenotazione();
    }

    @Test
    void restProdottoNonAstabileRestituisce422() throws Exception {
        jdbc.update("UPDATE prodotti SET astabile = FALSE WHERE id = ?", prodottoId);
        assertErroreRest(bodyRichiesta(), 422, "PRODOTTO_NON_ASTABILE");
        assertNessunaPrenotazione();
    }

    @Test
    void restStockEsauritoRestituisce409() throws Exception {
        jdbc.update("UPDATE prodotti SET quantita_disponibile = 0 WHERE id = ?", prodottoId);
        assertErroreRest(bodyRichiesta(), 409, "PRODOTTO_NON_DISPONIBILE");
        assertStock(0, 0);
        assertThat(contaAste()).isZero();
    }

    @Test
    void restProdottoInesistenteRestituisce404() throws Exception {
        Map<String, Object> body = bodyRichiesta();
        body.put("prodottoId", Long.MAX_VALUE);
        assertErroreRest(body, 404, "RISORSA_NON_TROVATA");
        assertNessunaPrenotazione();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(longs = {0, -1})
    void restIdProdottoNonValidoRestituisce400(Long id) throws Exception {
        Map<String, Object> body = bodyRichiesta();
        body.put("prodottoId", id);
        mvc.perform(post("/api/v1/admin/aste").header("Authorization", "Bearer " + token())
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        assertNessunaPrenotazione();
    }

    @Test
    void restJsonMalformatoRestituisce400() throws Exception {
        mvc.perform(post("/api/v1/admin/aste").header("Authorization", "Bearer " + token())
                        .contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        assertNessunaPrenotazione();
    }

    private Map<String, Object> bodyRichiesta() {
        ProgrammaAstaRequest request = richiesta();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("prodottoId", request.prodottoId());
        body.put("inizioLocale", request.inizioLocale().toString());
        body.put("timeZone", request.timeZone());
        body.put("prezzoIniziale", request.prezzoIniziale());
        return body;
    }

    private String token() throws Exception {
        String username = jdbc.queryForObject("SELECT username FROM utenti WHERE id = ?", String.class, adminId);
        return json.readTree(mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("username", username, "password", PASSWORD))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString())
                .get("accessToken").asText();
    }

    private void assertErroreRest(Map<String, Object> body, int status, String codice) throws Exception {
        mvc.perform(post("/api/v1/admin/aste").header("Authorization", "Bearer " + token())
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)))
                .andExpect(status().is(status))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value(codice))
                .andExpect(jsonPath("$.status").value(status));
    }

    private void assertNessunaPrenotazione() {
        assertStock(1, 0);
        assertThat(contaAste()).isZero();
    }

    @Test
    void salvaAstaEStockNellaStessaTransazione() {
        autenticaJwt(adminId, "ADMIN");
        ProgrammaAstaResponse asta = service.programmaAsta(adminId, richiesta());

        assertThat(asta.id()).isNotNull();
        assertThat(asta.stato()).isEqualTo(Stato.PROGRAMMATA);
        assertThat(asta.fineAt()).isEqualTo(asta.inizioAt().plusSeconds(7 * 60));
        assertStock(0, 1);
        assertThat(contaAste()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT admin_id FROM aste WHERE id = ?", Long.class, asta.id()))
                .isEqualTo(adminId);
    }

    @Test
    void erroreFaRollbackDiAstaEStock() {
        autenticaJwt(adminId, "ADMIN");
        TransactionTemplate transazione = new TransactionTemplate(transactionManager);
        assertThatThrownBy(() -> transazione.executeWithoutResult(status -> {
            service.programmaAsta(adminId, richiesta());
            throw new IllegalStateException("errore simulato dopo il salvataggio");
        })).isInstanceOf(IllegalStateException.class);

        assertStock(1, 0);
        assertThat(contaAste()).isZero();
    }

    @Test
    void dueProgrammazioniSullUltimaUnitaCreanoUnaSolaAsta() throws Exception {
        var concorrenti = Executors.newFixedThreadPool(2);
        CountDownLatch pronte = new CountDownLatch(2);
        CountDownLatch partenza = new CountDownLatch(1);
        try {
            var prima = concorrenti.submit(() -> programmaInConcorrenza(pronte, partenza));
            var seconda = concorrenti.submit(() -> programmaInConcorrenza(pronte, partenza));
            assertThat(pronte.await(5, TimeUnit.SECONDS)).isTrue();
            partenza.countDown();

            assertThat(List.of(prima.get(10, TimeUnit.SECONDS), seconda.get(10, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder("CREATA", "PRODOTTO_NON_DISPONIBILE");
            assertStock(0, 1);
            assertThat(contaAste()).isEqualTo(1);
        } finally {
            partenza.countDown();
            concorrenti.shutdownNow();
        }
    }

    @Test
    void userNonPuoProgrammareAste() {
        autenticaJwt(adminId, "USER");
        assertThatThrownBy(() -> service.programmaAsta(adminId, richiesta()))
                .isInstanceOf(AccessDeniedException.class);
        assertStock(1, 0);
        assertThat(contaAste()).isZero();
    }

    @Test
    void adminNonPuoAttribuireAstaAUnAltroUtente() {
        autenticaJwt(adminId + 1, "ADMIN");
        assertThatThrownBy(() -> service.programmaAsta(adminId, richiesta()))
                .isInstanceOf(AccessDeniedException.class);
        assertStock(1, 0);
        assertThat(contaAste()).isZero();
    }

    private String programmaInConcorrenza(CountDownLatch pronte, CountDownLatch partenza)
            throws InterruptedException {
        autenticaJwt(adminId, "ADMIN");
        try {
            pronte.countDown();
            if (!partenza.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Richieste concorrenti non avviate");
            }
            service.programmaAsta(adminId, richiesta());
            return "CREATA";
        } catch (AstaException exception) {
            String codice = (String) exception.getBody().getProperties().get("code");
            if (!"PRODOTTO_NON_DISPONIBILE".equals(codice)) {
                throw exception;
            }
            return codice;
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private ProgrammaAstaRequest richiesta() {
        return new ProgrammaAstaRequest(prodottoId,
                LocalDate.now(ZoneId.of("Europe/Rome")).plusDays(1).atTime(12, 0),
                "Europe/Rome", new BigDecimal("500.00"));
    }

    private int contaAste() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM aste WHERE prodotto_id = ?", Integer.class, prodottoId);
    }

    private void assertStock(int disponibile, int bloccata) {
        assertThat(jdbc.queryForObject("SELECT quantita_disponibile FROM prodotti WHERE id = ?",
                Integer.class, prodottoId)).isEqualTo(disponibile);
        assertThat(jdbc.queryForObject("SELECT quantita_bloccata FROM prodotti WHERE id = ?",
                Integer.class, prodottoId)).isEqualTo(bloccata);
    }

    private static void autenticaJwt(long id, String ruolo) {
        Instant adesso = Instant.now();
        Jwt jwt = Jwt.withTokenValue("token-test")
                .header("alg", "HS256")
                .subject(Long.toString(id))
                .issuedAt(adesso)
                .expiresAt(adesso.plusSeconds(60))
                .build();
        var contesto = SecurityContextHolder.createEmptyContext();
        contesto.setAuthentication(new JwtAuthenticationToken(jwt,
                List.of(new SimpleGrantedAuthority("ROLE_" + ruolo))));
        SecurityContextHolder.setContext(contesto);
    }

    @Test
    void lockRestaAttivoFinoAllaFineDellaTransazione() {
        autentica("ADMIN");
        TransactionTemplate transazione = new TransactionTemplate(transactionManager);
        var concorrente = Executors.newSingleThreadExecutor();
        try {
            transazione.executeWithoutResult(status -> {
                assertThat(service.bloccaProdottoPerProgrammazione(prodottoId).getId())
                        .isEqualTo(prodottoId);

                var secondaRichiesta = concorrente.submit(() -> transazione.execute(secondoStatus -> {
                    jdbc.execute("SET LOCAL lock_timeout = '500ms'");
                    return repository.trovaPerProgrammazioneConLock(prodottoId);
                }));

                assertThatThrownBy(() -> secondaRichiesta.get(5, TimeUnit.SECONDS))
                        .isInstanceOfSatisfying(ExecutionException.class, exception -> {
                            Throwable causa = exception.getCause();
                            while (causa.getCause() != null) {
                                causa = causa.getCause();
                            }
                            assertThat(causa).isInstanceOf(SQLException.class);
                            assertThat(((SQLException) causa).getSQLState()).isEqualTo("55P03");
                        });
            });

            // Dopo il commit il prodotto può essere bloccato da una nuova transazione.
            transazione.executeWithoutResult(status ->
                    assertThat(repository.trovaPerProgrammazioneConLock(prodottoId)).isPresent());
        } finally {
            concorrente.shutdownNow();
        }
    }

    @Test
    void adminDeveAvereUnaTransazioneAttiva() {
        autentica("ADMIN");
        assertThatThrownBy(() -> service.bloccaProdottoPerProgrammazione(prodottoId))
                .isInstanceOf(IllegalTransactionStateException.class);
    }

    @Test
    void userNonPuoBloccareUnProdottoPerProgrammare() {
        autentica("USER");
        TransactionTemplate transazione = new TransactionTemplate(transactionManager);
        assertThatThrownBy(() -> transazione.execute(status ->
                service.bloccaProdottoPerProgrammazione(prodottoId)))
                .isInstanceOf(AccessDeniedException.class);
    }

    private static void autentica(String ruolo) {
        var contesto = SecurityContextHolder.createEmptyContext();
        contesto.setAuthentication(new UsernamePasswordAuthenticationToken(
                "test-programmazione", null, List.of(new SimpleGrantedAuthority("ROLE_" + ruolo))));
        SecurityContextHolder.setContext(contesto);
    }
}
