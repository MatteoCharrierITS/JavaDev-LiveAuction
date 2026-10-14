package it.esercitazione.liveauction.producer.acquisto;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.esercitazione.liveauction.producer.asta.services.AstaService;
import it.esercitazione.liveauction.producer.asta.requests.ProgrammaAstaRequest;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.ErrorResponseException;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"spring.docker.compose.enabled=false","app.aste.scheduler.enabled=false"})
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named="RUN_DB_TESTS",matches="true")
@EnabledIfEnvironmentVariable(named="DB_URL",matches="jdbc:postgresql:.*")
class AcquistoIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired AcquistoService service;
    @Autowired AcquistiProperties config;
    @Autowired AstaService aste;
    private final List<Long> utenti=new ArrayList<>();
    private Account buyer,other,admin;
    private long category,product;

    @BeforeEach void prepare() throws Exception {
        admin=account("ADMIN"); buyer=account("USER"); other=account("USER"); config.setAdminId(admin.id());
        String tag=UUID.randomUUID().toString();
        category=jdbc.queryForObject("INSERT INTO categorie(nome,slug) VALUES (?,?) RETURNING id",Long.class,tag,tag);
        product=jdbc.queryForObject("INSERT INTO prodotti(categoria_id,sku,nome,prezzo_fisso,quantita_disponibile,quantita_bloccata,astabile) VALUES (?,?,'Purchase product',10,2,1,true) RETURNING id",Long.class,category,tag.substring(0,20));
        jdbc.update("UPDATE portafogli SET saldo_totale=1000 WHERE utente_id IN (?,?)",buyer.id(),other.id());
    }
    @AfterEach void cleanup() {
        config.setAdminId(0); SecurityContextHolder.clearContext();
        jdbc.update("DELETE FROM acquisti_fissi WHERE prodotto_id=?",product);
        jdbc.update("DELETE FROM aste WHERE prodotto_id=?",product);
        jdbc.update("DELETE FROM inventario_utenti WHERE prodotto_id=?",product);
        jdbc.update("DELETE FROM prodotti WHERE id=?",product);
        jdbc.update("DELETE FROM categorie WHERE id=?",category);
        for(long u:utenti) {
            jdbc.update("DELETE FROM movimenti_portafoglio WHERE portafoglio_id IN (SELECT id FROM portafogli WHERE utente_id=?)",u);
            jdbc.update("DELETE FROM portafogli WHERE utente_id=?",u);
            jdbc.update("DELETE FROM auth_sessions WHERE utente_id=?",u);
            jdbc.update("DELETE FROM utenti WHERE id=?",u);
        }
    }
    @Test void paymentCreditsConfiguredAdminAndOnlyAvailableStockIsSold() throws Exception {
        var id=UUID.randomUUID();
        mvc.perform(post(path()).header("Authorization",buyer.token()).contentType("application/json").content(body(id,2)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.totale").value(20))
                .andExpect(jsonPath("$.duplicato").value(false));
        assertThat(balance(buyer)).isEqualByComparingTo("980");
        assertThat(balance(admin)).isEqualByComparingTo("20");
        assertThat(number("SELECT quantita_disponibile FROM prodotti WHERE id=?",product)).isZero();
        assertThat(number("SELECT quantita_bloccata FROM prodotti WHERE id=?",product)).isEqualTo(1);
        assertThat(number("SELECT quantita FROM inventario_utenti WHERE utente_id=? AND prodotto_id=?",buyer.id(),product)).isEqualTo(2);
        assertThat(number("SELECT count(*) FROM movimenti_portafoglio WHERE tipo IN ('ACQUISTO_FISSO','INCASSO_FISSO') AND portafoglio_id IN (SELECT id FROM portafogli WHERE utente_id IN (?,?))",buyer.id(),admin.id())).isEqualTo(2);
    }
    @Test void retryReturnsOriginalResultEvenAfterCatalogueAndAdminConfigurationChange() throws Exception {
        UUID id=UUID.randomUUID();
        var first=json.readTree(mvc.perform(post(path()).header("Authorization",buyer.token()).contentType("application/json")
                .content(body(id,2))).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        jdbc.update("UPDATE prodotti SET attivo=false,prezzo_fisso=99 WHERE id=?",product); config.setAdminId(0);
        mvc.perform(post(path()).header("Authorization",buyer.token()).contentType("application/json").content(body(id,2)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.duplicato").value(true))
                .andExpect(jsonPath("$.totale").value(20)).andExpect(jsonPath("$.acquistatoAt").value(first.get("acquistatoAt").asText()));
        assertThat(balance(buyer)).isEqualByComparingTo("980");
        assertThat(number("SELECT count(*) FROM acquisti_fissi WHERE id=?",id)).isEqualTo(1);
    }
    @Test void uuidCannotBeReusedForDifferentUserOrQuantity() throws Exception {
        UUID id=UUID.randomUUID(); as(buyer,()->service.compra(buyer.id(),product,new AcquistoRequest(id,1)));
        mvc.perform(post(path()).header("Authorization",other.token()).contentType("application/json").content(body(id,1)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CLIENT_PURCHASE_ID_GIA_UTILIZZATO"));
        mvc.perform(post(path()).header("Authorization",buyer.token()).contentType("application/json").content(body(id,2)))
                .andExpect(status().isConflict());
    }
    @Test void accessChecksAndMissingConfigurationHaveNoEffects() throws Exception {
        mvc.perform(post(path()).contentType("application/json").content(body(UUID.randomUUID(),1))).andExpect(status().isUnauthorized());
        mvc.perform(post(path()).header("Authorization",admin.token()).contentType("application/json").content(body(UUID.randomUUID(),1))).andExpect(status().isForbidden());
        config.setAdminId(0);
        mvc.perform(post(path()).header("Authorization",buyer.token()).contentType("application/json").content(body(UUID.randomUUID(),1)))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("ACQUISTI_NON_CONFIGURATI"));
        assertThat(balance(buyer)).isEqualByComparingTo("1000");
    }
    @ParameterizedTest @ValueSource(strings={"0","101","null","1.5","\"abc\""})
    void invalidQuantityIsRejected(String quantity) throws Exception {
        mvc.perform(post(path()).header("Authorization",buyer.token()).contentType("application/json")
                .content("{\"clientPurchaseId\":\""+UUID.randomUUID()+"\",\"quantita\":"+quantity+"}"))
                .andExpect(status().isBadRequest());
    }
    @Test void reservedFundsAndBlockedStockCannotBeSpent() throws Exception {
        jdbc.update("UPDATE portafogli SET saldo_riservato=995 WHERE utente_id=?",buyer.id());
        mvc.perform(post(path()).header("Authorization",buyer.token()).contentType("application/json").content(body(UUID.randomUUID(),1)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SALDO_INSUFFICIENTE"));
        jdbc.update("UPDATE prodotti SET quantita_disponibile=0 WHERE id=?",product);
        mvc.perform(post(path()).header("Authorization",buyer.token()).contentType("application/json").content(body(UUID.randomUUID(),1)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PRODOTTO_NON_DISPONIBILE"));
        assertThat(balance(buyer)).isEqualByComparingTo("1000");
        assertThat(number("SELECT count(*) FROM acquisti_fissi WHERE prodotto_id=?",product)).isZero();
    }
    @Test void inactiveCategoryAndMissingPriceCannotBeBought() throws Exception {
        jdbc.update("UPDATE categorie SET attiva=false WHERE id=?",category);
        mvc.perform(post(path()).header("Authorization",buyer.token()).contentType("application/json").content(body(UUID.randomUUID(),1))).andExpect(status().isNotFound());
        jdbc.update("UPDATE categorie SET attiva=true WHERE id=?",category);
        jdbc.update("UPDATE prodotti SET prezzo_fisso=null WHERE id=?",product);
        mvc.perform(post(path()).header("Authorization",buyer.token()).contentType("application/json").content(body(UUID.randomUUID(),1)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PRODOTTO_NON_ACQUISTABILE"));
    }
    @Test void adminOverflowRollsBackBuyerLedgerStockInventoryAndPurchaseRecord() {
        jdbc.update("UPDATE portafogli SET saldo_totale=9999999999.99 WHERE utente_id=?",admin.id());
        assertThatThrownBy(()->as(buyer,()->service.compra(buyer.id(),product,new AcquistoRequest(UUID.randomUUID(),1))))
                .isInstanceOf(ErrorResponseException.class);
        assertThat(balance(buyer)).isEqualByComparingTo("1000");
        assertThat(number("SELECT count(*) FROM acquisti_fissi WHERE prodotto_id=?",product)).isZero();
        assertThat(number("SELECT count(*) FROM inventario_utenti WHERE prodotto_id=?",product)).isZero();
        assertThat(number("SELECT quantita_disponibile FROM prodotti WHERE id=?",product)).isEqualTo(2);
    }
    @Test void concurrentRetriesHaveOneEffect() throws Exception {
        UUID key=UUID.randomUUID();
        var results=concurrent(()->as(buyer,()->service.compra(buyer.id(),product,new AcquistoRequest(key,1))).duplicato(),
                ()->as(buyer,()->service.compra(buyer.id(),product,new AcquistoRequest(key,1))).duplicato());
        assertThat(results).containsExactlyInAnyOrder(false,true);
        assertThat(balance(buyer)).isEqualByComparingTo("990");
    }
    @Test void concurrentPurchasesOfLastUnitOnlyOneSucceeds() throws Exception {
        jdbc.update("UPDATE prodotti SET quantita_disponibile=1 WHERE id=?",product);
        var results=concurrent(()->outcome(()->as(buyer,()->service.compra(buyer.id(),product,new AcquistoRequest(UUID.randomUUID(),1)))),
                ()->outcome(()->as(other,()->service.compra(other.id(),product,new AcquistoRequest(UUID.randomUUID(),1)))));
        assertThat(results).containsExactlyInAnyOrder("OK","PRODOTTO_NON_DISPONIBILE");
        assertThat(number("SELECT sum(quantita) FROM inventario_utenti WHERE prodotto_id=?",product)).isEqualTo(1);
    }
    @Test void purchaseAndAuctionProgrammingCannotUseSameLastUnit() throws Exception {
        jdbc.update("UPDATE prodotti SET quantita_disponibile=1 WHERE id=?",product);
        var results=concurrent(()->outcome(()->as(buyer,()->service.compra(buyer.id(),product,new AcquistoRequest(UUID.randomUUID(),1)))),
                ()->outcome(()->as(admin,()->aste.programmaAsta(admin.id(),new ProgrammaAstaRequest(product,
                        LocalDateTime.now(ZoneId.of("Europe/Rome")).plusHours(1),"Europe/Rome",BigDecimal.ONE)))));
        assertThat(results).containsExactlyInAnyOrder("OK","PRODOTTO_NON_DISPONIBILE");
        assertThat(number("SELECT quantita_disponibile FROM prodotti WHERE id=?",product)).isZero();
    }
    private String path() {return "/api/v1/prodotti/"+product+"/acquisti";}
    private String body(UUID id,int quantity) {return "{\"clientPurchaseId\":\""+id+"\",\"quantita\":"+quantity+"}";}
    private long number(String sql,Object...args) {return jdbc.queryForObject(sql,Long.class,args);}
    private BigDecimal balance(Account u) {return jdbc.queryForObject("SELECT saldo_totale FROM portafogli WHERE utente_id=?",BigDecimal.class,u.id());}
    private Account account(String role) throws Exception {
        String name="buy_"+UUID.randomUUID().toString().substring(0,12);
        long id=json.readTree(mvc.perform(post("/api/v1/auth/register").contentType("application/json")
                .content(json.writeValueAsString(Map.of("username",name,"email",name+"@example.invalid","password","FixturePassword123!"))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asLong();
        utenti.add(id); jdbc.update("UPDATE utenti SET ruolo=? WHERE id=?",role,id);
        String token=json.readTree(mvc.perform(post("/api/v1/auth/login").contentType("application/json")
                .content(json.writeValueAsString(Map.of("username",name,"password","FixturePassword123!"))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("accessToken").asText();
        return new Account(id,"Bearer "+token,role);
    }
    private <T> T as(Account u,Supplier<T> action) {
        var jwt=Jwt.withTokenValue("fixture").header("alg","HS256").subject(Long.toString(u.id())).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt,List.of(new SimpleGrantedAuthority("ROLE_"+u.role()))));
        try {return action.get();} finally {SecurityContextHolder.clearContext();}
    }
    private String outcome(Supplier<?> call) {
        try {call.get();return "OK";} catch(ErrorResponseException e) {return (String)e.getBody().getProperties().get("code");}
    }
    private <T> List<T> concurrent(Callable<T> a,Callable<T> b) throws Exception {
        try(var pool=Executors.newFixedThreadPool(2)) {
            var start=new CountDownLatch(1);
            var first=pool.submit(()->{start.await();return a.call();});
            var second=pool.submit(()->{start.await();return b.call();}); start.countDown();
            return List.of(first.get(15,TimeUnit.SECONDS),second.get(15,TimeUnit.SECONDS));
        }
    }
    record Account(long id,String token,String role) {}
}
