package it.esercitazione.liveauction.producer.asta;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.esercitazione.liveauction.producer.asta.events.AstaTransizioneEvent;
import it.esercitazione.liveauction.producer.asta.exceptions.AstaException;
import it.esercitazione.liveauction.producer.asta.exceptions.OffertaException;
import it.esercitazione.liveauction.producer.asta.models.Stato;
import it.esercitazione.liveauction.producer.asta.requests.OffertaRequest;
import it.esercitazione.liveauction.producer.asta.services.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.event.EventListener;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"spring.docker.compose.enabled=false", "app.aste.scheduler.enabled=false",
        "spring.datasource.url=${DB_URL}"})
@EnabledIfEnvironmentVariable(named = "RUN_DB_TESTS", matches = "true")
@EnabledIfEnvironmentVariable(named = "DB_URL", matches = "jdbc:postgresql:.*")
@AutoConfigureMockMvc
@Import(AstaGestioneIntegrationTests.EventConfig.class)
class AstaGestioneIntegrationTests {
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired PasswordEncoder encoder;
    @Autowired AnnullamentoAstaService annullamento;
    @Autowired StoricoAstaService storico;
    @Autowired OffertaService offerte;
    @Autowired ChiusuraAstaService chiusura;
    @Autowired AstaLifecycleService lifecycle;
    @Autowired PlatformTransactionManager tx;
    @Autowired EventCollector collector;

    private long categoria, prodotto, altroProdotto, admin, user, altroUser;
    private String suffix, adminToken, userToken, altroToken;
    private Instant now;

    @BeforeEach
    void prepara() throws Exception {
        suffix = UUID.randomUUID().toString().substring(0, 8);
        now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        categoria = jdbc.queryForObject("INSERT INTO categorie (nome, slug) VALUES (?, ?) RETURNING id",
                Long.class, "Gestione " + suffix, "gestione-" + suffix);
        prodotto = prodotto("GEST-");
        altroProdotto = prodotto("ALT-");
        admin = utente("ADMIN", "admin_");
        user = utente("USER", "user_");
        altroUser = utente("USER", "altro_");
        adminToken = token(admin);
        userToken = token(user);
        altroToken = token(altroUser);
        collector.eventi.clear();
    }

    @AfterEach
    void pulisci() {
        SecurityContextHolder.clearContext();
        for (long p : List.of(prodotto, altroProdotto)) {
            jdbc.update("DELETE FROM movimenti_portafoglio WHERE asta_id IN (SELECT id FROM aste WHERE prodotto_id = ?)", p);
            jdbc.update("DELETE FROM offerte WHERE asta_id IN (SELECT id FROM aste WHERE prodotto_id = ?)", p);
            jdbc.update("DELETE FROM aste WHERE prodotto_id = ?", p);
            jdbc.update("DELETE FROM inventario_utenti WHERE prodotto_id = ?", p);
            jdbc.update("DELETE FROM prodotti WHERE id = ?", p);
        }
        jdbc.update("DELETE FROM categorie WHERE id = ?", categoria);
        for (long u : List.of(admin, user, altroUser)) {
            jdbc.update("DELETE FROM auth_sessions WHERE utente_id = ?", u);
            jdbc.update("DELETE FROM portafogli WHERE utente_id = ?", u);
            jdbc.update("DELETE FROM utenti WHERE id = ?", u);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"PROGRAMMATA", "STANZA_APERTA", "APERTA"})
    void annullaStatiAmmessiUnaSolaVoltaEPubblicaDopoCommit(String stato) throws Exception {
        long id = asta(prodotto, stato, null, now);
        var prima = mvc.perform(post("/api/v1/admin/aste/{id}/annullamento", id)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.stato").value("ANNULLATA"))
                .andExpect(jsonPath("$.sequence").value(1))
                .andReturn().getResponse().getContentAsString();
        var seconda = mvc.perform(post("/api/v1/admin/aste/{id}/annullamento", id)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(json.readTree(seconda)).isEqualTo(json.readTree(prima));
        assertStock(10, 0);
        assertThat(valoreLong("SELECT versione FROM prodotti WHERE id = ?", prodotto)).isEqualTo(1);
        assertThat(valoreLong("SELECT versione FROM aste WHERE id = ?", id)).isEqualTo(1);
        assertThat(eventi(id)).singleElement().satisfies(e -> {
            assertThat(e.type()).isEqualTo(AstaTransizioneEvent.Tipo.AUCTION_CANCELLED);
            assertThat(e.sequence()).isEqualTo(1);
            assertThat(e.stato()).isEqualTo(Stato.ANNULLATA);
            assertThat(valoreLong("SELECT sequence FROM aste WHERE id = ?", id)).isEqualTo(e.sequence());
        });
        assertThat(lifecycle.aggiornaStato(id)).isZero();
        assertThat(chiusura.chiudi(id).stato()).isEqualTo("ANNULLATA");
        mvc.perform(get("/api/v1/aste/{id}", id)).andExpect(jsonPath("$.offerteConsentite").value(false));
        mvc.perform(post("/api/v1/aste/{id}/ticket", id).header("Authorization", "Bearer " + userToken))
                .andExpect(status().isConflict());
    }

    @Test
    void rollbackRipristinaStockStatoESequenceSenzaEvento() {
        long id = asta(prodotto, "PROGRAMMATA", null, now.plusSeconds(600));
        autentica(admin, "ADMIN");
        assertThatThrownBy(() -> new TransactionTemplate(tx).executeWithoutResult(s -> {
            annullamento.annulla(id);
            assertThat(eventi(id)).isEmpty();
            throw new IllegalStateException("rollback richiesto");
        })).isInstanceOf(IllegalStateException.class);
        assertStock(9, 1);
        assertThat(valoreLong("SELECT sequence FROM aste WHERE id = ?", id)).isZero();
        assertThat(valoreLong("SELECT versione FROM prodotti WHERE id = ?", prodotto)).isZero();
        assertThat(jdbc.queryForObject("SELECT stato FROM aste WHERE id = ?", String.class, id)).isEqualTo("PROGRAMMATA");
        assertThat(eventi(id)).isEmpty();
        assertThat(annullamento.annulla(id).sequence()).isEqualTo(1);
        assertStock(10, 0);
    }

    @Test
    void stockIncoerenteNonAnnullaEPubblicaNulla() {
        long id = asta(prodotto, "PROGRAMMATA", null, now.plusSeconds(600));
        jdbc.update("UPDATE prodotti SET quantita_bloccata = 0 WHERE id = ?", prodotto);
        autentica(admin, "ADMIN");
        assertThatThrownBy(() -> annullamento.annulla(id)).hasRootCauseInstanceOf(IllegalStateException.class);
        assertThat(valoreLong("SELECT sequence FROM aste WHERE id = ?", id)).isZero();
        assertThat(eventi(id)).isEmpty();
    }

    @Test
    void chiusaEScadutaNonSonoAnnullabili() throws Exception {
        for (long id : List.of(asta(prodotto, "CHIUSA", null, now.minusSeconds(900)),
                asta(prodotto, "APERTA", null, now.minusSeconds(900)))) {
            mvc.perform(post("/api/v1/admin/aste/{id}/annullamento", id).header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ASTA_NON_ANNULLABILE"));
            assertThat(eventi(id)).isEmpty();
        }
        assertStock(9, 1);
    }

    @Test
    void offerteRicevuteAncheRitirateImpedisconoAnnullamentoSenzaToccareFondi() throws Exception {
        long id = asta(prodotto, "APERTA", null, now.minusSeconds(10));
        autentica(user, "USER");
        offerte.piazza(user, id, new OffertaRequest("PLACE_BID", UUID.randomUUID(), new BigDecimal("10.00"), 0L));
        SecurityContextHolder.clearContext();
        mvc.perform(post("/api/v1/admin/aste/{id}/annullamento", id).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ASTA_CON_OFFERTE"));
        assertThat(valoreLong("SELECT saldo_riservato FROM portafogli WHERE utente_id = ?", user)).isEqualTo(10);
        jdbc.update("UPDATE offerte SET leader = FALSE, ritirata_at = CURRENT_TIMESTAMP WHERE asta_id = ?", id);
        jdbc.update("UPDATE aste SET offerta_corrente = NULL WHERE id = ?", id);
        mvc.perform(post("/api/v1/admin/aste/{id}/annullamento", id).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ASTA_CON_OFFERTE"));
        assertStock(9, 1);
        assertThat(eventi(id)).isEmpty();
    }

    @Test
    void accessiEValidazioneAnnullamento() throws Exception {
        long id = asta(prodotto, "PROGRAMMATA", null, now.plusSeconds(600));
        mvc.perform(post("/api/v1/admin/aste/{id}/annullamento", id)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/admin/aste/{id}/annullamento", id).header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/admin/aste/{id}/annullamento", Long.MAX_VALUE).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("RISORSA_NON_TROVATA"));
        mvc.perform(post("/api/v1/admin/aste/0/annullamento").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isBadRequest());
        autentica(user, "USER");
        assertThatThrownBy(() -> annullamento.annulla(id)).isInstanceOf(AccessDeniedException.class);
        assertStock(9, 1);
    }

    @Test
    void annullamentiConcorrentiNonDuplicanoRilascioOEvento() throws Exception {
        long id = asta(prodotto, "APERTA", null, now.minusSeconds(10));
        assertThat(concorrenza(() -> annullaComeAdmin(id), () -> annullaComeAdmin(id)))
                .containsExactly("ANNULLATA", "ANNULLATA");
        assertStock(10, 0);
        assertThat(eventi(id)).hasSize(1);
        assertThat(valoreLong("SELECT sequence FROM aste WHERE id = ?", id)).isEqualTo(1);
    }

    @Test
    void concorrenzaConOffertaProduceUnSoloEsitoSenzaDoppiRilasci() throws Exception {
        long id = asta(prodotto, "APERTA", null, now.minusSeconds(10));
        var risultati = concorrenza(() -> annullaComeAdmin(id), () -> {
            autentica(user, "USER");
            try {
                offerte.piazza(user, id, new OffertaRequest("PLACE_BID", UUID.randomUUID(), new BigDecimal("10.00"), 0L));
                return "OFFERTA";
            } catch (OffertaException e) {
                return (String) e.getBody().getProperties().get("code");
            }
        });
        if (risultati.contains("ANNULLATA")) {
            assertThat(risultati).containsExactlyInAnyOrder("ANNULLATA", "ASTA_NON_APERTA");
            assertStock(10, 0);
            assertThat(valoreLong("SELECT saldo_riservato FROM portafogli WHERE utente_id = ?", user)).isZero();
            assertThat(valoreLong("SELECT COUNT(*) FROM offerte WHERE asta_id = ?", id)).isZero();
            assertThat(valoreLong("SELECT COUNT(*) FROM movimenti_portafoglio WHERE asta_id = ?", id)).isZero();
        } else {
            assertThat(risultati).containsExactlyInAnyOrder("ASTA_CON_OFFERTE", "OFFERTA");
            assertStock(9, 1);
            assertThat(valoreLong("SELECT saldo_riservato FROM portafogli WHERE utente_id = ?", user)).isEqualTo(10);
            assertThat(valoreLong("SELECT COUNT(*) FROM offerte WHERE asta_id = ?", id)).isEqualTo(1);
            assertThat(eventi(id)).isEmpty();
        }
    }

    @Test
    void concorrenzaConChiusuraNonRilasciaDueVolte() throws Exception {
        long id = asta(prodotto, "APERTA", null, now.minusSeconds(900));
        var risultati = concorrenza(() -> annullaComeAdmin(id), () -> chiusura.chiudi(id).stato());
        assertThat(risultati).containsExactlyInAnyOrder("ASTA_NON_ANNULLABILE", "CHIUSA");
        assertStock(10, 0);
        assertThat(valoreLong("SELECT sequence FROM aste WHERE id = ?", id)).isEqualTo(1);
        assertThat(eventi(id)).isEmpty();
        assertThat(chiusura.chiudi(id).stato()).isEqualTo("CHIUSA");
        assertStock(10, 0);
    }

    @Test
    void vittorieUsanoSoloIdentitaDelTokenConPaginaStabile() throws Exception {
        long prima = asta(prodotto, "CHIUSA", user, now.minusSeconds(900));
        long seconda = asta(altroProdotto, "CHIUSA", user, now.minusSeconds(900));
        asta(prodotto, "CHIUSA", altroUser, now.minusSeconds(900));
        asta(prodotto, "CHIUSA", null, now.minusSeconds(900));
        asta(prodotto, "ANNULLATA", null, now.minusSeconds(900));
        asta(prodotto, "APERTA", user, now.minusSeconds(10));
        jdbc.update("UPDATE prodotti SET attivo = FALSE WHERE id = ?", altroProdotto);
        String body = mvc.perform(get("/api/v1/me/vittorie").header("Authorization", "Bearer " + userToken)
                        .param("utenteId", Long.toString(altroUser)).param("size", "1"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.totalElements").value(2)).andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.content[0].id").value(seconda))
                .andExpect(jsonPath("$.content[0].prodotto.id").value(altroProdotto))
                .andExpect(jsonPath("$.content[0].prezzoFinale").value(10))
                .andExpect(jsonPath("$.content[0].chiusaAt").value(now.toString()))
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("password", "email", "username", "vincitoreId", "adminId");
        mvc.perform(get("/api/v1/me/vittorie").header("Authorization", "Bearer " + userToken).param("size", "1").param("page", "1"))
                .andExpect(jsonPath("$.content[0].id").value(prima));
        mvc.perform(get("/api/v1/me/vittorie").header("Authorization", "Bearer " + altroToken))
                .andExpect(jsonPath("$.totalElements").value(1));
        autentica(user, "USER");
        assertThatThrownBy(() -> storico.vittorie(altroUser, 0, 20)).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void vittorieVuoteEPaginaOltreIlTotale() throws Exception {
        mvc.perform(get("/api/v1/me/vittorie").header("Authorization", "Bearer " + userToken))
                .andExpect(jsonPath("$.totalElements").value(0)).andExpect(jsonPath("$.totalPages").value(0))
                .andExpect(jsonPath("$.content.length()").value(0));
        asta(prodotto, "CHIUSA", user, now.minusSeconds(900));
        mvc.perform(get("/api/v1/me/vittorie").header("Authorization", "Bearer " + userToken).param("page", "10"))
                .andExpect(jsonPath("$.totalElements").value(1)).andExpect(jsonPath("$.content.length()").value(0));
    }

    @Test
    void storicoCombinaFiltriEIncludeEntrambiGliEstremiTemporali() throws Exception {
        long prima = asta(prodotto, "CHIUSA", user, now.minusSeconds(900));
        long seconda = asta(prodotto, "CHIUSA", user, now.minusSeconds(900));
        jdbc.update("UPDATE aste SET chiusa_at = ? WHERE id = ?", Timestamp.from(now.minusSeconds(60)), prima);
        asta(altroProdotto, "CHIUSA", user, now.minusSeconds(900));
        asta(prodotto, "CHIUSA", altroUser, now.minusSeconds(900));
        asta(prodotto, "APERTA", null, now.minusSeconds(10));
        mvc.perform(get("/api/v1/admin/aste/storico").header("Authorization", "Bearer " + adminToken)
                        .param("prodottoId", Long.toString(prodotto)).param("vincitoreId", Long.toString(user))
                        .param("stato", "CHIUSA").param("da", now.minusSeconds(60).toString()).param("a", now.toString()).param("size", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].id").value(seconda)).andExpect(jsonPath("$.totalPages").value(2));
        mvc.perform(get("/api/v1/admin/aste/storico").header("Authorization", "Bearer " + adminToken)
                        .param("prodottoId", Long.toString(prodotto)).param("vincitoreId", Long.toString(user))
                        .param("da", now.toString()).param("a", now.toString()))
                .andExpect(jsonPath("$.totalElements").value(1)).andExpect(jsonPath("$.content[0].id").value(seconda));
        mvc.perform(get("/api/v1/admin/aste/storico").header("Authorization", "Bearer " + adminToken)
                        .param("prodottoId", Long.toString(prodotto)).param("vincitoreId", Long.toString(user))
                        .param("da", now.minusSeconds(60).toString()).param("a", now.toString()).param("size", "1").param("page", "1"))
                .andExpect(jsonPath("$.content[0].id").value(prima));
    }

    @Test
    void storicoIncludeAnnullateESenzaVincitoreEConservaUtentiAnonimizzati() throws Exception {
        long chiusa = asta(prodotto, "CHIUSA", null, now.minusSeconds(900));
        long vinta = asta(prodotto, "CHIUSA", user, now.minusSeconds(900));
        long annullata = asta(prodotto, "PROGRAMMATA", null, now.plusSeconds(600));
        autentica(admin, "ADMIN");
        annullamento.annulla(annullata);
        SecurityContextHolder.clearContext();
        jdbc.update("UPDATE utenti SET attivo = FALSE, username = ? WHERE id = ?", "deleted_" + suffix, user);
        String body = mvc.perform(get("/api/v1/admin/aste/storico").header("Authorization", "Bearer " + adminToken)
                        .param("prodottoId", Long.toString(prodotto)))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content[0].id").value(annullata))
                .andExpect(jsonPath("$.content[0].vincitoreId").value(nullValue()))
                .andExpect(jsonPath("$.content[0].prezzoFinale").value(nullValue()))
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("password", "email", "username", "deleted_");
        mvc.perform(get("/api/v1/admin/aste/storico").header("Authorization", "Bearer " + adminToken)
                        .param("prodottoId", Long.toString(prodotto)).param("stato", "CHIUSA"))
                .andExpect(jsonPath("$.content[0].id").value(vinta))
                .andExpect(jsonPath("$.content[1].id").value(chiusa))
                .andExpect(jsonPath("$.content[1].prezzoFinale").value(nullValue()));
        mvc.perform(get("/api/v1/admin/aste/storico").header("Authorization", "Bearer " + adminToken)
                        .param("prodottoId", Long.toString(prodotto)).param("stato", "ANNULLATA"))
                .andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get("/api/v1/admin/aste/storico").header("Authorization", "Bearer " + adminToken)
                        .param("prodottoId", Long.toString(Long.MAX_VALUE)))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void ruoliHttpEServizioPerStorici() throws Exception {
        for (String path : List.of("/api/v1/me/vittorie", "/api/v1/admin/aste/storico")) {
            mvc.perform(get(path)).andExpect(status().isUnauthorized());
        }
        mvc.perform(get("/api/v1/me/vittorie").header("Authorization", "Bearer " + adminToken)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/admin/aste/storico").header("Authorization", "Bearer " + userToken)).andExpect(status().isForbidden());
        autentica(user, "USER");
        assertThatThrownBy(() -> storico.storico(null, null, null, null, null, 0, 20)).isInstanceOf(AccessDeniedException.class);
        autentica(admin, "ADMIN");
        assertThatThrownBy(() -> storico.vittorie(admin, 0, 20)).isInstanceOf(AccessDeniedException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"?page=-1", "?size=0", "?size=101", "?page=abc", "?size=abc"})
    void paginazioneNonValida(String query) throws Exception {
        mvc.perform(get("/api/v1/me/vittorie" + query).header("Authorization", "Bearer " + userToken))
                .andExpect(status().isBadRequest()).andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        mvc.perform(get("/api/v1/admin/aste/storico" + query).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isBadRequest()).andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @ParameterizedTest
    @ValueSource(strings = {"?stato=APERTA", "?stato=INVENTATO", "?prodottoId=0", "?vincitoreId=-1", "?da=abc", "?a=abc",
            "?da=2026-10-12T12:00:00Z&a=2026-10-11T12:00:00Z"})
    void filtriNonValidi(String query) throws Exception {
        mvc.perform(get("/api/v1/admin/aste/storico" + query).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isBadRequest()).andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    private List<String> concorrenza(Supplier<String> prima, Supplier<String> seconda) throws Exception {
        try (var executor = Executors.newFixedThreadPool(2)) {
            var pronte = new CountDownLatch(2);
            var partenza = new CountDownLatch(1);
            var a = executor.submit(() -> esegui(prima, pronte, partenza));
            var b = executor.submit(() -> esegui(seconda, pronte, partenza));
            assertThat(pronte.await(5, TimeUnit.SECONDS)).isTrue();
            partenza.countDown();
            return List.of(a.get(15, TimeUnit.SECONDS), b.get(15, TimeUnit.SECONDS));
        }
    }

    private String esegui(Supplier<String> azione, CountDownLatch pronte, CountDownLatch partenza) throws Exception {
        pronte.countDown();
        if (!partenza.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Avvio concorrente fallito");
        try { return azione.get(); } finally { SecurityContextHolder.clearContext(); }
    }

    private String annullaComeAdmin(long id) {
        autentica(admin, "ADMIN");
        try { return annullamento.annulla(id).stato().name(); }
        catch (AstaException e) { return (String) e.getBody().getProperties().get("code"); }
    }

    private List<AstaTransizioneEvent> eventi(long id) {
        return collector.eventi.stream().filter(e -> e.auctionId() == id).toList();
    }

    private long prodotto(String prefisso) {
        return jdbc.queryForObject("INSERT INTO prodotti (categoria_id, sku, nome, astabile, quantita_disponibile) VALUES (?, ?, ?, TRUE, 10) RETURNING id",
                Long.class, categoria, prefisso + suffix, "Prodotto " + prefisso + suffix);
    }

    private long utente(String ruolo, String prefisso) {
        long id = jdbc.queryForObject("INSERT INTO utenti (username, email, password_hash, ruolo) VALUES (?, ?, ?, ?) RETURNING id",
                Long.class, prefisso + suffix, prefisso + suffix + "@example.invalid", encoder.encode("TestMarco123!"), ruolo);
        jdbc.update("INSERT INTO portafogli (utente_id, saldo_totale) VALUES (?, 1000)", id);
        return id;
    }

    private String token(long utente) throws Exception {
        String username = jdbc.queryForObject("SELECT username FROM utenti WHERE id = ?", String.class, utente);
        return json.readTree(mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("username", username, "password", "TestMarco123!"))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("accessToken").asText();
    }

    private long asta(long p, String stato, Long vincitore, Instant inizio) {
        boolean terminale = List.of("CHIUSA", "ANNULLATA").contains(stato);
        long id = jdbc.queryForObject("""
                INSERT INTO aste (prodotto_id, admin_id, stato, prezzo_iniziale, inizio_at, fine_at,
                    vincitore_id, offerta_corrente, chiusa_at, annullata_at)
                VALUES (?, ?, ?, 10, ?, ?, ?, ?, ?, ?) RETURNING id
                """, Long.class, p, admin, stato, Timestamp.from(inizio), Timestamp.from(inizio.plusSeconds(420)),
                vincitore, vincitore == null ? null : BigDecimal.TEN,
                "CHIUSA".equals(stato) ? Timestamp.from(now) : null,
                "ANNULLATA".equals(stato) ? Timestamp.from(now) : null);
        if (!terminale) jdbc.update("UPDATE prodotti SET quantita_disponibile = quantita_disponibile - 1, quantita_bloccata = quantita_bloccata + 1 WHERE id = ?", p);
        return id;
    }

    private long valoreLong(String sql, long id) { return jdbc.queryForObject(sql, Long.class, id); }
    private void assertStock(int libera, int bloccata) {
        assertThat(valoreLong("SELECT quantita_disponibile FROM prodotti WHERE id = ?", prodotto)).isEqualTo(libera);
        assertThat(valoreLong("SELECT quantita_bloccata FROM prodotti WHERE id = ?", prodotto)).isEqualTo(bloccata);
    }

    private static void autentica(long id, String ruolo) {
        Jwt jwt = Jwt.withTokenValue("test").header("alg", "HS256").subject(Long.toString(id))
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).build();
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("ROLE_" + ruolo))));
        SecurityContextHolder.setContext(context);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class EventConfig {
        @Bean EventCollector gestioneEventCollector() { return new EventCollector(); }
    }
    static class EventCollector {
        final List<AstaTransizioneEvent> eventi = new CopyOnWriteArrayList<>();
        @EventListener public void ricevi(AstaTransizioneEvent evento) { eventi.add(evento); }
    }
}
