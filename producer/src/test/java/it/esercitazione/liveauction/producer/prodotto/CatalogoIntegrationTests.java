package it.esercitazione.liveauction.producer.prodotto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import it.esercitazione.liveauction.producer.prodotto.repos.ProdottoRepository;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.docker.compose.enabled=false")
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "RUN_DB_TESTS", matches = "true")
class CatalogoIntegrationTests {
    private static final String PASSWORD = "TestPassword123!";

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper json;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired ProdottoRepository prodotti;
    @Autowired PlatformTransactionManager transactions;

    @Test
    void categoryDeactivationHidesProductsAndReactivationRestoresThem() throws Exception {
        Fixture f = fixture();
        mvc.perform(put("/api/v1/admin/categorie/{id}", f.categoryId())
                        .header("Authorization", "Bearer " + f.adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new Categoria("Updated " + f.slug(), f.slug(), false))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.attiva").value(false));
        mvc.perform(get("/api/v1/categorie"))
                .andExpect(jsonPath("$[?(@.id == " + f.categoryId() + ")]").isEmpty());
        mvc.perform(get("/api/v1/prodotti").param("categoria", f.slug()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/api/v1/prodotti/{id}", f.productId()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RISORSA_NON_TROVATA"));
        mvc.perform(get("/api/v1/admin/prodotti/{id}", f.productId())
                        .header("Authorization", "Bearer " + f.adminToken()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.attivo").value(true))
                .andExpect(jsonPath("$.quantitaDisponibile").value(3));
        mvc.perform(get("/api/v1/admin/prodotti").param("categoria", f.slug()).param("attivo", "true")
                        .header("Authorization", "Bearer " + f.adminToken()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(put("/api/v1/admin/categorie/{id}", f.categoryId())
                        .header("Authorization", "Bearer " + f.adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new Categoria("Updated " + f.slug(), f.slug(), true))))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/prodotti").param("categoria", f.slug()))
                .andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get("/api/v1/prodotti/{id}", f.productId()))
                .andExpect(status().isOk());
    }

    @Test
    void countsOnlyNonConcludedAuctionsInListAndDetail() throws Exception {
        Fixture f = fixture();
        Long adminId = jdbc.queryForObject("SELECT id FROM utenti WHERE username = ?", Long.class, f.adminUsername());
        for (String state : new String[]{"PROGRAMMATA", "STANZA_APERTA", "APERTA", "CHIUSA", "ANNULLATA"}) {
            jdbc.update("""
                    INSERT INTO aste (prodotto_id, admin_id, stato, prezzo_iniziale, inizio_at, fine_at)
                    VALUES (?, ?, ?, 10.00, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP + INTERVAL '7 minutes')
                    """, f.productId(), adminId, state);
        }
        mvc.perform(get("/api/v1/prodotti").param("categoria", f.slug()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].asteProgrammate").value(3));
        mvc.perform(get("/api/v1/prodotti/{id}", f.productId()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.asteProgrammate").value(3));
    }

    @Test
    void invalidBodiesPaginationAndDuplicatesReturnProblemDetails() throws Exception {
        Fixture f = fixture();
        mvc.perform(put("/api/v1/admin/prodotti/{id}", f.productId())
                        .header("Authorization", "Bearer " + f.adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"astabile\":true,\"quantitaDisponibile\":3}"))
                .andExpect(status().isBadRequest())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        mvc.perform(get("/api/v1/prodotti").param("page", "-1"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/prodotti").param("size", "0"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/admin/prodotti")
                        .header("Authorization", "Bearer " + f.adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new NuovoProdotto(f.categoryId(), "BAD", "Bad", null,
                                new BigDecimal("1.001"), true, -1))))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/admin/categorie")
                        .header("Authorization", "Bearer " + f.adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new Categoria("Another", f.slug(), true))))
                .andExpect(status().isConflict())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("CATEGORIA_GIA_ESISTENTE"));
        mvc.perform(get("/api/v1/admin/prodotti")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/admin/prodotti")
                        .header("Authorization", "Bearer " + token("user_" + UUID.randomUUID().toString().substring(0, 8), false)))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminFiltersInactiveProductsAndCannotOverwriteChangedStock() throws Exception {
        Fixture f = fixture();
        jdbc.update("UPDATE prodotti SET quantita_disponibile = 2, quantita_bloccata = 1, versione = versione + 1 WHERE id = ?",
                f.productId());
        ModificaProdotto stale = new ModificaProdotto(f.categoryId(), f.sku(), "Changed", null,
                null, true, 10, false, 0L);
        mvc.perform(put("/api/v1/admin/prodotti/{id}", f.productId())
                        .header("Authorization", "Bearer " + f.adminToken())
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(stale)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("VERSIONE_NON_AGGIORNATA"));
        ModificaProdotto fresh = new ModificaProdotto(f.categoryId(), f.sku(), "Changed", null,
                null, true, 2, false, 1L);
        mvc.perform(put("/api/v1/admin/prodotti/{id}", f.productId())
                        .header("Authorization", "Bearer " + f.adminToken())
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(fresh)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.quantitaBloccata").value(1));
        mvc.perform(get("/api/v1/admin/prodotti").param("categoria", f.slug()).param("attivo", "false")
                        .param("astabile", "true").param("query", f.sku().toLowerCase())
                        .header("Authorization", "Bearer " + f.adminToken()))
                .andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get("/api/v1/admin/prodotti").param("categoria", f.slug()).param("attivo", "true")
                        .header("Authorization", "Bearer " + f.adminToken()))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void concurrentStockUpdatesAllowOnlyOneCommit() throws Exception {
        Fixture f = fixture();
        CyclicBarrier readBarrier = new CyclicBarrier(2);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> updateStock(f.productId(), 7, readBarrier));
            var second = executor.submit(() -> updateStock(f.productId(), 9, readBarrier));
            assertThat(java.util.List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
        }
        var saved = prodotti.findById(f.productId()).orElseThrow();
        assertThat(saved.getVersione()).isEqualTo(1);
        assertThat(saved.getQuantitaDisponibile()).isIn(7, 9);
        assertThat(saved.getQuantitaBloccata()).isZero();
    }

    private boolean updateStock(long id, int quantity, CyclicBarrier barrier) {
        try {
            new TransactionTemplate(transactions).executeWithoutResult(tx -> {
                var product = prodotti.findById(id).orElseThrow();
                try {
                    barrier.await(10, TimeUnit.SECONDS);
                } catch (Exception exception) {
                    throw new IllegalStateException(exception);
                }
                product.setQuantitaDisponibile(quantity);
                prodotti.saveAndFlush(product);
            });
            return true;
        } catch (OptimisticLockingFailureException exception) {
            return false;
        }
    }

    private Fixture fixture() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String username = "admin_" + suffix;
        String adminToken = token(username, true);
        String slug = "test-" + suffix;
        long categoryId = json.readTree(mvc.perform(post("/api/v1/admin/categorie")
                        .header("Authorization", "Bearer " + adminToken).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new Categoria("Test " + suffix, slug, true))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asLong();
        String sku = "TST-" + suffix;
        long productId = json.readTree(mvc.perform(post("/api/v1/admin/prodotti")
                        .header("Authorization", "Bearer " + adminToken).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new NuovoProdotto(categoryId, sku, "Laptop " + suffix,
                                null, new BigDecimal("12.50"), true, 3))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asLong();
        return new Fixture(adminToken, username, categoryId, slug, productId, sku);
    }

    private record Fixture(String adminToken, String adminUsername, long categoryId, String slug,
                           long productId, String sku) {}

    @Test
    void adminManagesCatalogAndVisitorsSeeOnlyActiveProducts() throws Exception {
        String suffisso = UUID.randomUUID().toString().substring(0, 8);
        String adminToken = token("admin_" + suffisso, true);
        String userToken = token("user_" + suffisso, false);
        String slug = "test-" + suffisso;

        String categoriaBody = json.writeValueAsString(new Categoria("Test " + suffisso, slug, null));
        mvc.perform(post("/api/v1/admin/categorie")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON).content(categoriaBody))
                .andExpect(status().isForbidden());
        long categoriaId = json.readTree(mvc.perform(post("/api/v1/admin/categorie")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(categoriaBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.attiva").value(true))
                .andReturn().getResponse().getContentAsString()).get("id").asLong();
        mvc.perform(post("/api/v1/admin/categorie")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(categoriaBody))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CATEGORIA_GIA_ESISTENTE"));
        mvc.perform(get("/api/v1/categorie"))
                .andExpect(status().isOk());

        String sku = "tst-" + suffisso;
        String prodottoBody = json.writeValueAsString(new NuovoProdotto(categoriaId, sku,
                "Laptop " + suffisso, "Prodotto di prova", new BigDecimal("1299.90"), true, 3));
        JsonNode creato = json.readTree(mvc.perform(post("/api/v1/admin/prodotti")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(prodottoBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sku").value(sku.toUpperCase()))
                .andExpect(jsonPath("$.quantitaBloccata").value(0))
                .andExpect(jsonPath("$.versione").value(0))
                .andReturn().getResponse().getContentAsString());
        long prodottoId = creato.get("id").asLong();
        mvc.perform(post("/api/v1/admin/prodotti")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(prodottoBody))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SKU_GIA_UTILIZZATO"));

        mvc.perform(get("/api/v1/prodotti")
                        .param("query", suffisso).param("categoria", slug).param("astabile", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(prodottoId))
                .andExpect(jsonPath("$.content[0].asteProgrammate").value(0));
        mvc.perform(get("/api/v1/prodotti").param("size", "101"))
                .andExpect(status().isBadRequest());

        String modifica = json.writeValueAsString(new ModificaProdotto(categoriaId, sku,
                "Laptop " + suffisso, null, null, false, 5, false, 0L));
        mvc.perform(put("/api/v1/admin/prodotti/{id}", prodottoId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(modifica))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantitaDisponibile").value(5))
                .andExpect(jsonPath("$.attivo").value(false))
                .andExpect(jsonPath("$.versione").value(1));
        mvc.perform(put("/api/v1/admin/prodotti/{id}", prodottoId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(modifica))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("VERSIONE_NON_AGGIORNATA"));

        mvc.perform(get("/api/v1/prodotti/{id}", prodottoId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RISORSA_NON_TROVATA"));
        mvc.perform(get("/api/v1/admin/prodotti/{id}", prodottoId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categoria.slug").value(slug));
    }

    private String token(String username, boolean admin) throws Exception {
        mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new Registration(username, username + "@example.com", PASSWORD))))
                .andExpect(status().isCreated());
        if (admin) {
            jdbc.update("UPDATE utenti SET ruolo = 'ADMIN' WHERE username = ?", username);
        }
        return json.readTree(mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new Login(username, PASSWORD))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()).get("accessToken").asText();
    }

    private record Registration(String username, String email, String password) {
    }

    private record Login(String username, String password) {
    }

    private record Categoria(String nome, String slug, Boolean attiva) {
    }

    private record NuovoProdotto(Long categoriaId, String sku, String nome, String descrizione,
                                 BigDecimal prezzoFisso, Boolean astabile, Integer quantitaDisponibile) {
    }

    private record ModificaProdotto(Long categoriaId, String sku, String nome, String descrizione,
                                    BigDecimal prezzoFisso, Boolean astabile, Integer quantitaDisponibile,
                                    Boolean attivo, Long versione) {
    }
}
