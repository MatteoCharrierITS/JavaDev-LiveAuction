package it.esercitazione.liveauction.producer.inventario;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.esercitazione.liveauction.producer.inventario.services.InventarioService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;

import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"spring.docker.compose.enabled=false", "app.aste.scheduler.enabled=false"})
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named="RUN_DB_TESTS", matches="true")
@EnabledIfEnvironmentVariable(named="DB_URL", matches="jdbc:postgresql:.*")
class InventarioIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired InventarioService service;
    private final List<Long> utenti = new ArrayList<>();
    private final List<Long> prodotti = new ArrayList<>();
    private long categoria;
    private Account primo, altro, admin;

    @BeforeEach void prepara() throws Exception {
        primo=account(false); altro=account(false); admin=account(true);
        String tag=UUID.randomUUID().toString();
        categoria=jdbc.queryForObject("INSERT INTO categorie(nome,slug) VALUES (?,?) RETURNING id",Long.class,tag,tag);
    }
    @AfterEach void pulisci() {
        SecurityContextHolder.clearContext();
        for(long p:prodotti) {
            jdbc.update("DELETE FROM inventario_utenti WHERE prodotto_id=?",p);
            jdbc.update("DELETE FROM prodotti WHERE id=?",p);
        }
        jdbc.update("DELETE FROM categorie WHERE id=?",categoria);
        for(long u:utenti) {
            jdbc.update("DELETE FROM movimenti_portafoglio WHERE portafoglio_id IN (SELECT id FROM portafogli WHERE utente_id=?)",u);
            jdbc.update("DELETE FROM auth_sessions WHERE utente_id=?",u);
            jdbc.update("DELETE FROM portafogli WHERE utente_id=?",u);
            jdbc.update("DELETE FROM utenti WHERE id=?",u);
        }
    }
    @Test void soloUserAutenticato() throws Exception {
        mvc.perform(get("/api/v1/me/inventario")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/me/inventario").header("Authorization",admin.bearer())).andExpect(status().isForbidden());
    }
    @Test void vuotoEIsolamentoTraUtenti() throws Exception {
        long p=prodotto();
        jdbc.update("INSERT INTO inventario_utenti(utente_id,prodotto_id,quantita) VALUES (?,?,3)",altro.id(),p);
        mvc.perform(get("/api/v1/me/inventario").header("Authorization",primo.bearer())
                .param("utenteId",Long.toString(altro.id())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.content").isEmpty());
    }
    @Test void quantitaPositiveEProdottiNonPiuPubblici() throws Exception {
        long p=prodotto(), zero=prodotto();
        jdbc.update("INSERT INTO inventario_utenti(utente_id,prodotto_id,quantita) VALUES (?,?,4),(?,?,0)",primo.id(),p,primo.id(),zero);
        jdbc.update("UPDATE prodotti SET attivo=false WHERE id=?",p);
        jdbc.update("UPDATE categorie SET attiva=false WHERE id=?",categoria);
        String body=mvc.perform(get("/api/v1/me/inventario").header("Authorization",primo.bearer()))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"))
                .andExpect(jsonPath("$.content[0].prodotto.id").value(p))
                .andExpect(jsonPath("$.content[0].prodotto.attivo").value(false))
                .andExpect(jsonPath("$.content[0].prodotto.categoria.attiva").value(false))
                .andExpect(jsonPath("$.content[0].quantita").value(4))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("password","email","utenteId","saldo");
    }
    @Test void paginazioneStabileEPaginaOltreFine() throws Exception {
        long p1=prodotto(),p2=prodotto();
        jdbc.update("INSERT INTO inventario_utenti(utente_id,prodotto_id,quantita) VALUES (?,?,1),(?,?,2)",primo.id(),p2,primo.id(),p1);
        mvc.perform(get("/api/v1/me/inventario").header("Authorization",primo.bearer()).param("size","1").param("page","1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].prodotto.id").value(p2))
                .andExpect(jsonPath("$.totalPages").value(2));
        mvc.perform(get("/api/v1/me/inventario").header("Authorization",primo.bearer()).param("page","100"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content").isEmpty());
    }
    @ParameterizedTest @ValueSource(strings={"?page=-1","?size=0","?size=101","?page=abc"})
    void paginazioneNonValida(String query) throws Exception {
        mvc.perform(get("/api/v1/me/inventario"+query).header("Authorization",primo.bearer()))
                .andExpect(status().isBadRequest()).andExpect(content().contentTypeCompatibleWith("application/problem+json"));
    }
    @Test void servizioNonConsenteDiLeggereAltriUtenti() {
        var jwt=Jwt.withTokenValue("fixture").header("alg","HS256").subject(Long.toString(primo.id())).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt,List.of(new SimpleGrantedAuthority("ROLE_USER"))));
        assertThatThrownBy(()->service.personale(altro.id(),0,20)).isInstanceOf(AccessDeniedException.class);
    }
    private long prodotto() {
        long id=jdbc.queryForObject("INSERT INTO prodotti(categoria_id,sku,nome) VALUES (?,?,'Inventory product') RETURNING id",
                Long.class,categoria,UUID.randomUUID().toString().substring(0,20));
        prodotti.add(id); return id;
    }
    private Account account(boolean admin) throws Exception {
        String name="inv_"+UUID.randomUUID().toString().substring(0,12);
        var registration=json.readTree(mvc.perform(post("/api/v1/auth/register").contentType("application/json")
                .content(json.writeValueAsString(Map.of("username",name,"email",name+"@example.invalid","password","FixturePassword123!"))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        long id=registration.get("id").asLong(); utenti.add(id);
        if(admin) jdbc.update("UPDATE utenti SET ruolo='ADMIN' WHERE id=?",id);
        String token=json.readTree(mvc.perform(post("/api/v1/auth/login").contentType("application/json")
                .content(json.writeValueAsString(Map.of("username",name,"password","FixturePassword123!"))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("accessToken").asText();
        return new Account(id,"Bearer "+token);
    }
    record Account(long id,String bearer) {}
}
