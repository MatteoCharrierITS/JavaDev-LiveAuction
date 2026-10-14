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
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc
class InventoryConsumerTests {
    @TestConfiguration static class Config {
        @Bean Stub inventoryStub() {return new Stub();}
        @Bean @Primary RestClient inventoryClient(Stub stub) {return stub.builder.build();}
    }
    static class Stub {
        final RestClient.Builder builder=RestClient.builder().baseUrl("http://producer.test/api/v1");
        final MockRestServiceServer server=MockRestServiceServer.bindTo(builder).build();
    }
    @Autowired MockMvc mvc;
    @Autowired Stub stub;
    @BeforeEach void reset() {stub.server.reset();}
    private MockHttpSession session(String role) {
        var s=new MockHttpSession();
        s.setAttribute(AuthSessionService.ATTRIBUTE,new SessionAuth("inventory-token",Instant.now().plusSeconds(600),
                "refresh",Instant.now().plusSeconds(3600),7L,"viewer",role)); return s;
    }
    @Test void protectedAndValidatesPage() throws Exception {
        mvc.perform(get("/inventario")).andExpect(status().is3xxRedirection());
        mvc.perform(get("/inventario").session(session("ADMIN"))).andExpect(status().isForbidden());
        mvc.perform(get("/inventario").session(session("USER")).param("size","101")).andExpect(status().isBadRequest());
        stub.server.verify();
    }
    @Test void rendersOwnedInactiveProductsAndPaginationWithoutTokens() throws Exception {
        stub.server.expect(requestTo("http://producer.test/api/v1/me/inventario?page=0&size=20"))
                .andExpect(header("Authorization","Bearer inventory-token"))
                .andRespond(withSuccess("""
                  {"content":[{"prodotto":{"id":3,"sku":"TEST","nome":"Owned keyboard","attivo":false,
                  "categoria":{"id":1,"nome":"Hardware","attiva":false}},"quantita":2}],
                  "page":0,"size":20,"totalElements":21,"totalPages":2}
                  """,MediaType.APPLICATION_JSON));
        mvc.perform(get("/inventario").session(session("USER"))).andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Cache-Control","no-store"))
                .andExpect(content().string(containsString("Owned keyboard")))
                .andExpect(content().string(containsString("No longer listed")))
                .andExpect(content().string(containsString("page=1")))
                .andExpect(content().string(not(containsString("inventory-token"))));
        stub.server.verify();
    }
    @Test void emptyInventoryAndProducerUnavailable() throws Exception {
        stub.server.expect(requestTo("http://producer.test/api/v1/me/inventario?page=0&size=20"))
                .andRespond(withSuccess("{\"content\":[],\"page\":0,\"size\":20,\"totalElements\":0,\"totalPages\":0}",MediaType.APPLICATION_JSON));
        mvc.perform(get("/inventario").session(session("USER"))).andExpect(status().isOk())
                .andExpect(content().string(containsString("No products on this page")));
        stub.server.verify(); stub.server.reset();
        stub.server.expect(requestTo("http://producer.test/api/v1/me/inventario?page=0&size=20"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        mvc.perform(get("/inventario").session(session("USER"))).andExpect(status().isServiceUnavailable());
        stub.server.verify();
    }
}
