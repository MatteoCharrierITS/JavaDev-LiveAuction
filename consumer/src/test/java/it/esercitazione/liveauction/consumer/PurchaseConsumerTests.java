package it.esercitazione.liveauction.consumer;

import it.esercitazione.liveauction.consumer.auth.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.*;
import org.springframework.context.annotation.*;
import org.springframework.http.*;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.RestClient;
import java.time.Instant;
import java.util.UUID;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc
class PurchaseConsumerTests {
    @TestConfiguration static class Config {
        @Bean Stub purchaseStub() {return new Stub();}
        @Bean @Primary RestClient purchaseClient(Stub stub) {return stub.builder.build();}
    }
    static class Stub {
        final RestClient.Builder builder=RestClient.builder().baseUrl("http://producer.test/api/v1");
        final MockRestServiceServer server=MockRestServiceServer.bindTo(builder).build();
    }
    @Autowired MockMvc mvc;
    @Autowired Stub stub;
    @BeforeEach void reset() {stub.server.reset();}
    private MockHttpSession session(String role) {
        var session=new MockHttpSession(); session.setAttribute(AuthSessionService.ATTRIBUTE,
                new SessionAuth("purchase-token",Instant.now().plusSeconds(600),"refresh",Instant.now().plusSeconds(3600),7L,"buyer",role)); return session;
    }
    private void expect(HttpStatus status,String body) {
        stub.server.expect(requestTo("http://producer.test/api/v1/prodotti/3/acquisti"))
                .andExpect(method(HttpMethod.POST)).andExpect(header("Authorization","Bearer purchase-token"))
                .andRespond(withStatus(status).contentType(status.isError()?MediaType.APPLICATION_PROBLEM_JSON:MediaType.APPLICATION_JSON).body(body));
    }
    @Test void roleCsrfAndValidationProtectPurchase() throws Exception {
        String id=UUID.randomUUID().toString();
        mvc.perform(post("/prodotti/3/acquisti").with(csrf()).param("clientPurchaseId",id).param("quantita","1")).andExpect(status().is3xxRedirection());
        mvc.perform(post("/prodotti/3/acquisti").session(session("ADMIN")).with(csrf()).param("clientPurchaseId",id).param("quantita","1")).andExpect(status().isForbidden());
        mvc.perform(post("/prodotti/3/acquisti").session(session("USER")).param("clientPurchaseId",id).param("quantita","1")).andExpect(status().isForbidden());
        mvc.perform(post("/prodotti/3/acquisti").session(session("USER")).with(csrf()).param("clientPurchaseId",id).param("quantita","101")).andExpect(status().isBadRequest());
        stub.server.verify();
    }
    @Test void confirmedPurchaseRedirectsToReloadedInventory() throws Exception {
        UUID id=UUID.randomUUID();
        expect(HttpStatus.CREATED,"{\"id\":\""+id+"\",\"prodottoId\":3,\"quantita\":2,\"totale\":20,\"duplicato\":false}");
        mvc.perform(post("/prodotti/3/acquisti").session(session("USER")).with(csrf())
                .param("clientPurchaseId",id.toString()).param("quantita","2"))
                .andExpect(redirectedUrl("/inventario")).andExpect(flash().attributeExists("notice"));
        stub.server.verify();
    }
    @Test void unknownOutcomePreservesUuidAndQuantityInSafeRetryForm() throws Exception {
        UUID id=UUID.randomUUID(); expect(HttpStatus.SERVICE_UNAVAILABLE,"{}");
        mvc.perform(post("/prodotti/3/acquisti").session(session("USER")).with(csrf())
                .param("clientPurchaseId",id.toString()).param("quantita","2"))
                .andExpect(status().isServiceUnavailable()).andExpect(content().string(containsString(id.toString())))
                .andExpect(content().string(containsString("Retry the same purchase safely")))
                .andExpect(content().string(not(containsString("purchase-token"))))
                .andExpect(model().attribute("retryQuantity",2));
        stub.server.verify();
    }
    @Test void insufficientBalanceIsVisibleWithoutUnsafeNewPurchase() throws Exception {
        expect(HttpStatus.CONFLICT,"{\"code\":\"SALDO_INSUFFICIENTE\"}");
        mvc.perform(post("/prodotti/3/acquisti").session(session("USER")).with(csrf())
                .param("clientPurchaseId",UUID.randomUUID().toString()).param("quantita","1"))
                .andExpect(status().isConflict()).andExpect(content().string(containsString("Reserved bid funds cannot be spent")));
        stub.server.verify();
    }
}
