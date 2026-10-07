package it.esercitazione.liveauction.producer.asta;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import it.esercitazione.liveauction.producer.asta.models.Stato;
import it.esercitazione.liveauction.producer.asta.services.AstaLifecycleService;
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
import org.springframework.test.web.servlet.MockMvc;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"spring.docker.compose.enabled=false", "app.aste.scheduler.enabled=false",
        "spring.datasource.url=${DB_URL}"})
@EnabledIfEnvironmentVariable(named = "RUN_DB_TESTS", matches = "true")
@EnabledIfEnvironmentVariable(named = "DB_URL", matches = "jdbc:postgresql:.*")
@AutoConfigureMockMvc
class AstaLetturaIntegrationTests {

    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired AstaLifecycleService lifecycle;

    private final List<Long> prodotti = new ArrayList<>();
    private final List<Long> categorie = new ArrayList<>();
    private final List<Long> utenti = new ArrayList<>();
    private long prodottoId;
    private long altroProdottoId;
    private long adminId;
    private long primoOfferente;
    private long secondoOfferente;
    private String slug;
    private String suffisso;
    private Instant inizio;

    @BeforeEach
    void preparaRecordIsolati() {
        suffisso = UUID.randomUUID().toString().substring(0, 8);
        slug = "lettura-" + suffisso;
        long categoria = categoria(slug);
        long altraCategoria = categoria("altro-" + suffisso);
        prodottoId = prodotto(categoria, "Laptop 100%_!\\ " + suffisso, "Portatile professionale");
        altroProdottoId = prodotto(altraCategoria, "Monitor " + suffisso, "Schermo da tavolo");
        adminId = utente("admin_" + suffisso, "ADMIN");
        primoOfferente = utente("marco_" + suffisso + "a", "USER");
        secondoOfferente = utente("giovanni_" + suffisso + "i", "USER");
        inizio = Instant.now().truncatedTo(ChronoUnit.SECONDS).minusSeconds(30);
    }

    @AfterEach
    void eliminaSoloIRecordDelTest() {
        for (long prodotto : prodotti) {
            jdbc.update("DELETE FROM offerte WHERE asta_id IN (SELECT id FROM aste WHERE prodotto_id = ?)", prodotto);
            jdbc.update("DELETE FROM aste WHERE prodotto_id = ?", prodotto);
            jdbc.update("DELETE FROM prodotti WHERE id = ?", prodotto);
        }
        categorie.forEach(id -> jdbc.update("DELETE FROM categorie WHERE id = ?", id));
        utenti.forEach(id -> jdbc.update("DELETE FROM utenti WHERE id = ?", id));
    }

    @Test
    void snapshotPubblicoConOrariUtcENessunaOfferta() throws Exception {
        long asta = asta(prodottoId, Stato.STANZA_APERTA, inizio);
        String body = mvc.perform(get("/api/v1/aste/{id}", asta))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.id").value(asta))
                .andExpect(jsonPath("$.stato").value("STANZA_APERTA"))
                .andExpect(jsonPath("$.offertaCorrente").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.migliorOfferente").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.numeroOfferte").value(0))
                .andExpect(jsonPath("$.prezzoIniziale").value(500))
                .andExpect(jsonPath("$.incrementoMinimo").value(1))
                .andExpect(jsonPath("$.offerteConsentite").value(false))
                .andExpect(jsonPath("$.sequence").value(1))
                .andExpect(jsonPath("$.serverTime", matchesPattern(".*Z$")))
                .andReturn().getResponse().getContentAsString();
        JsonNode risposta = json.readTree(body);
        assertThat(Instant.parse(risposta.get("inizioAt").asText())).isEqualTo(inizio);
        assertThat(Instant.parse(risposta.get("aperturaStanzaAt").asText())).isEqualTo(inizio.minusSeconds(180));
        assertThat(Instant.parse(risposta.get("fineAt").asText())).isEqualTo(inizio.plusSeconds(420));
        assertThat(body).doesNotContain("password", "email", "admin", "versione", "dataCreazione");
        assertThat(risposta.get("prodotto").size()).isEqualTo(2);
        assertThat(risposta.has("vincitore")).isFalse();
    }

    @Test
    void leggeLeaderConteggioEScadenzaEstesaDalleOfferte() throws Exception {
        long asta = asta(prodottoId, Stato.APERTA, inizio);
        offerta(asta, primoOfferente, 500);
        offerta(asta, secondoOfferente, 630);
        Instant fine = inizio.plusSeconds(460);
        jdbc.update("UPDATE aste SET offerta_corrente = 630, fine_at = ?, sequence = 4 WHERE id = ?",
                Timestamp.from(fine), asta);
        String body = mvc.perform(get("/api/v1/aste/{id}", asta))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.numeroOfferte").value(2))
                .andExpect(jsonPath("$.migliorOfferente.id").value(secondoOfferente))
                .andExpect(jsonPath("$.migliorOfferente.displayName").value("g***i"))
                .andExpect(jsonPath("$.offertaCorrente").value(630))
                .andExpect(jsonPath("$.fineAt").value(fine.toString()))
                .andExpect(jsonPath("$.sequence").value(4))
                .andExpect(jsonPath("$.offerteConsentite").value(true))
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("giovanni_", "username", "password", "email");
        mvc.perform(get("/api/v1/aste").param("categoria", slug))
                .andExpect(jsonPath("$.content[0].numeroOfferte").value(2))
                .andExpect(jsonPath("$.content[0].offertaCorrente").value(630))
                .andExpect(jsonPath("$.content[0].fineAt").value(fine.toString()));
    }

    @Test
    void esponeRisultatoChiusoSenzaDatiRiservatiDelVincitore() throws Exception {
        long asta = asta(prodottoId, Stato.CHIUSA, inizio.minusSeconds(600));
        offerta(asta, secondoOfferente, 630);
        jdbc.update("UPDATE aste SET offerta_corrente = 630, vincitore_id = ?, chiusa_at = ? WHERE id = ?",
                secondoOfferente, Timestamp.from(inizio), asta);
        String body = mvc.perform(get("/api/v1/aste/{id}", asta))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.vincitore.id").value(secondoOfferente))
                .andExpect(jsonPath("$.vincitore.displayName").value("g***i"))
                .andExpect(jsonPath("$.prezzoFinale").value(630))
                .andExpect(jsonPath("$.chiusaAt").value(inizio.toString()))
                .andExpect(jsonPath("$.offerteConsentite").value(false))
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("giovanni_", "email", "password", "admin");
    }

    @Test
    void astaChiusaSenzaOfferteNonInventaUnVincitoreOUnPrezzoFinale() throws Exception {
        long asta = asta(prodottoId, Stato.CHIUSA, inizio.minusSeconds(600));
        jdbc.update("UPDATE aste SET chiusa_at = ? WHERE id = ?", Timestamp.from(inizio), asta);
        mvc.perform(get("/api/v1/aste/{id}", asta))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.numeroOfferte").value(0))
                .andExpect(jsonPath("$.vincitore").doesNotExist())
                .andExpect(jsonPath("$.prezzoFinale").doesNotExist())
                .andExpect(jsonPath("$.chiusaAt").value(inizio.toString()));
    }

    @Test
    void astaApertaGiaScadutaNonConsenteOfferte() throws Exception {
        long asta = asta(prodottoId, Stato.APERTA, inizio.minusSeconds(600));
        mvc.perform(get("/api/v1/aste/{id}", asta))
                .andExpect(status().isOk()).andExpect(jsonPath("$.offerteConsentite").value(false));
        mvc.perform(get("/api/v1/aste").param("categoria", slug))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].offerteConsentite").value(false));
    }

    @Test
    void leLettureNonAttivanoAsteONonModificanoStockELoSnapshotSegueIlJob() throws Exception {
        long asta = asta(prodottoId, Stato.PROGRAMMATA, inizio);
        int disponibili = jdbc.queryForObject("SELECT quantita_disponibile FROM prodotti WHERE id = ?",
                Integer.class, prodottoId);
        mvc.perform(get("/api/v1/aste/{id}", asta))
                .andExpect(jsonPath("$.stato").value("PROGRAMMATA"))
                .andExpect(jsonPath("$.sequence").value(0));
        mvc.perform(get("/api/v1/aste").param("categoria", slug)).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT quantita_disponibile FROM prodotti WHERE id = ?",
                Integer.class, prodottoId)).isEqualTo(disponibili);
        assertThat(jdbc.queryForObject("SELECT quantita_bloccata FROM prodotti WHERE id = ?",
                Integer.class, prodottoId)).isEqualTo(1);
        lifecycle.aggiornaStato(asta);
        mvc.perform(get("/api/v1/aste/{id}", asta))
                .andExpect(jsonPath("$.stato").value("APERTA"))
                .andExpect(jsonPath("$.sequence").value(2))
                .andExpect(jsonPath("$.offerteConsentite").value(true));
    }

    @Test
    void lobbyPubblicaPaginataConOrdinamentoStabile() throws Exception {
        long prima = asta(prodottoId, Stato.PROGRAMMATA, inizio.plusSeconds(600));
        long seconda = asta(prodottoId, Stato.PROGRAMMATA, inizio.plusSeconds(600));
        long terza = asta(prodottoId, Stato.STANZA_APERTA, inizio.plusSeconds(800));
        mvc.perform(get("/api/v1/aste").param("categoria", slug).param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.page").value(0)).andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalElements").value(3)).andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].id").value(prima))
                .andExpect(jsonPath("$.content[1].id").value(seconda))
                .andExpect(jsonPath("$.serverTime", matchesPattern(".*Z$")));
        mvc.perform(get("/api/v1/aste").param("categoria", slug).param("page", "1").param("size", "2"))
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(terza));
        mvc.perform(get("/api/v1/aste").param("categoria", slug).param("page", "2147483647").param("size", "100"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content.length()").value(0))
                .andExpect(jsonPath("$.totalElements").value(3));
    }

    @Test
    void combinaFiltriDiStatoCategoriaETesto() throws Exception {
        long asta = asta(prodottoId, Stato.APERTA, inizio);
        asta(prodottoId, Stato.PROGRAMMATA, inizio.plusSeconds(600));
        asta(altroProdottoId, Stato.APERTA, inizio);
        mvc.perform(get("/api/v1/aste").param("stato", "APERTA").param("categoria", " " + slug + " ")
                        .param("query", " lApToP "))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(asta));
    }

    @ParameterizedTest
    @ValueSource(strings = {"100%_!\\", "%", "_", "!", "\\", "professionale"})
    void laRicercaTrattaIWildcardComeTestoLetterale(String testo) throws Exception {
        long asta = asta(prodottoId, Stato.PROGRAMMATA, inizio);
        asta(altroProdottoId, Stato.PROGRAMMATA, inizio);
        mvc.perform(get("/api/v1/aste").param("query", testo))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(asta));
    }

    @Test
    void categoriaSconosciutaRestituiscePaginaVuota() throws Exception {
        mvc.perform(get("/api/v1/aste").param("categoria", "inesistente-" + suffisso))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content.length()").value(0))
                .andExpect(jsonPath("$.page").value(0)).andExpect(jsonPath("$.size").value(12))
                .andExpect(jsonPath("$.totalElements").value(0)).andExpect(jsonPath("$.totalPages").value(0));
    }

    @Test
    void laDisattivazioneDelProdottoNonNascondeAsteEStorico() throws Exception {
        long asta = asta(prodottoId, Stato.CHIUSA, inizio);
        jdbc.update("UPDATE prodotti SET attivo = FALSE WHERE id = ?", prodottoId);
        mvc.perform(get("/api/v1/aste/{id}", asta)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/aste").param("categoria", slug))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].id").value(asta));
    }

    @ParameterizedTest
    @ValueSource(longs = {0, -1, Long.MAX_VALUE})
    void astaInesistenteRestituisceProblemDetail404(long id) throws Exception {
        mvc.perform(get("/api/v1/aste/{id}", id))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("RISORSA_NON_TROVATA"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"?page=-1", "?size=0", "?size=101", "?size=abc", "?page=abc",
            "?page=2147483648", "?stato=INVENTATO", "/abc"})
    void parametriInvalidiRestituisconoProblemDetail400(String parametri) throws Exception {
        mvc.perform(get("/api/v1/aste" + parametri))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    private long categoria(String nome) {
        long id = jdbc.queryForObject("INSERT INTO categorie (nome, slug) VALUES (?, ?) RETURNING id",
                Long.class, nome, nome);
        categorie.add(id);
        return id;
    }

    private long prodotto(long categoria, String nome, String descrizione) {
        long id = jdbc.queryForObject("""
                INSERT INTO prodotti (categoria_id, sku, nome, descrizione, astabile, quantita_disponibile)
                VALUES (?, ?, ?, ?, TRUE, 10) RETURNING id
                """, Long.class, categoria, "LET-" + UUID.randomUUID().toString().substring(0, 8), nome, descrizione);
        prodotti.add(id);
        return id;
    }

    private long utente(String nome, String ruolo) {
        long id = jdbc.queryForObject("""
                INSERT INTO utenti (username, email, password_hash, ruolo)
                VALUES (?, ?, 'test-non-usato', ?) RETURNING id
                """, Long.class, nome, nome + "@example.invalid", ruolo);
        utenti.add(id);
        return id;
    }

    private long asta(long prodotto, Stato stato, Instant inizio) {
        long sequence = stato == Stato.PROGRAMMATA ? 0 : stato == Stato.STANZA_APERTA ? 1 : 2;
        long id = jdbc.queryForObject("""
                INSERT INTO aste (prodotto_id, admin_id, stato, prezzo_iniziale, inizio_at, fine_at, sequence)
                VALUES (?, ?, ?, 500, ?, ?, ?) RETURNING id
                """, Long.class, prodotto, adminId, stato.name(), Timestamp.from(inizio),
                Timestamp.from(inizio.plusSeconds(420)), sequence);
        if (stato != Stato.CHIUSA && stato != Stato.ANNULLATA) {
            jdbc.update("""
                    UPDATE prodotti SET quantita_disponibile = quantita_disponibile - 1,
                    quantita_bloccata = quantita_bloccata + 1 WHERE id = ?
                    """, prodotto);
        }
        return id;
    }

    private void offerta(long asta, long utente, int importo) {
        jdbc.update("INSERT INTO offerte (asta_id, offerente_id, client_bid_id, importo) VALUES (?, ?, ?, ?)",
                asta, utente, UUID.randomUUID(), importo);
    }
}
