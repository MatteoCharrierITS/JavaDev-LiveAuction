package it.esercitazione.liveauction.consumer;

import it.esercitazione.liveauction.consumer.auth.AuthSessionService;
import it.esercitazione.liveauction.consumer.auth.SessionAuth;
import it.esercitazione.liveauction.consumer.client.ProducerClient;
import it.esercitazione.liveauction.consumer.client.ProducerException;
import it.esercitazione.liveauction.consumer.client.ProducerUnavailableException;
import it.esercitazione.liveauction.consumer.dto.RegistrationRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.RestClient;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

@SpringBootTest
@AutoConfigureMockMvc
class AuthFlowTests {
    @TestConfiguration
    static class StubConfig {
        @Bean StubHolder stubHolder() { return new StubHolder(); }
        @Bean @Primary RestClient stubProducerRestClient(StubHolder holder) { return holder.builder.build(); }
    }

    static class StubHolder {
        final RestClient.Builder builder = RestClient.builder().baseUrl("http://producer.test/api/v1");
        final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    }

    @BeforeEach void reset() { stub.server.reset(); }
    @Autowired MockMvc mvc;
    @Autowired ProducerClient producer;
    @Autowired AuthSessionService sessions;
    @Autowired StubHolder stub;

    private void respond(String path, HttpStatus status, String body, MediaType type) {
        stub.server.reset();
        stub.server.expect(requestTo("http://producer.test/api/v1" + path))
                .andRespond(withStatus(status).contentType(type).body(body));
    }

    @Test void publicPagesRoleGatesAndCsrf() throws Exception {
        mvc.perform(get("/login")).andExpect(status().isOk());
        mvc.perform(get("/registrazione")).andExpect(status().isOk());
        mvc.perform(get("/aste/42")).andExpect(redirectedUrl("/login?expired=0"));
        mvc.perform(get("/admin/prodotti")).andExpect(status().is3xxRedirection());
        mvc.perform(get("/admin/prodotti").session(session("USER", false))).andExpect(status().isForbidden());
        mvc.perform(get("/aste/42").session(session("USER", false))).andExpect(status().isNotFound());
        mvc.perform(get("/admin/prodotti").session(session("ADMIN", false))).andExpect(status().isNotFound());
        mvc.perform(post("/login").param("username", "alice").param("password", "password123"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/registrazione").param("username", "alice").param("email", "a@example.com")
                .param("password", "password123")).andExpect(status().isForbidden());
        mvc.perform(post("/logout").session(session("USER", false))).andExpect(status().isForbidden());
        mvc.perform(post("/account/delete").session(session("USER", false))).andExpect(status().isForbidden());
        stub.server.verify();
    }

    @Test void registrationLoginAndErrors() throws Exception {
        mvc.perform(post("/registrazione").with(csrf()).param("username", "a")
                .param("email", "bad").param("password", "short"))
                .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("Use 3 to 50 characters")));
        stub.server.verify();
        respond("/auth/register", HttpStatus.CONFLICT,
                "{\"status\":409,\"code\":\"USERNAME_GIA_UTILIZZATO\",\"detail\":\"private input\"}", MediaType.APPLICATION_PROBLEM_JSON);
        mvc.perform(post("/registrazione").with(csrf()).param("username", "alice")
                .param("email", "a@example.com").param("password", "password123"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("already in use")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("private input"))));
        respond("/auth/register", HttpStatus.CREATED,
                "{\"id\":7,\"username\":\"alice\",\"email\":\"a@example.com\",\"ruolo\":\"USER\"}", MediaType.APPLICATION_JSON);
        mvc.perform(post("/registrazione").with(csrf()).param("username", "alice")
                .param("email", "a@example.com").param("password", "password123"))
                .andExpect(redirectedUrl("/login?registered=1"));
        respond("/auth/login", HttpStatus.UNAUTHORIZED, "{\"status\":401}", MediaType.APPLICATION_PROBLEM_JSON);
        mvc.perform(post("/login").with(csrf()).param("username", "alice").param("password", "wrongpass"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Invalid username or password")));
        respond("/auth/login", HttpStatus.OK, loginResponse("access-one", "refresh-one"), MediaType.APPLICATION_JSON);
        var result = mvc.perform(post("/login").with(csrf()).param("username", "alice")
                .param("password", "password123")).andExpect(redirectedUrl("/account")).andReturn();
        MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
        assertThat(((SessionAuth) session.getAttribute(AuthSessionService.ATTRIBUTE)).refreshToken()).isEqualTo("refresh-one");
        mvc.perform(get("/account").session(session)).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("access-one"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("refresh-one"))));
    }

    @Test void refreshRotatesBothTokensAndFailedRefreshClearsSession() throws Exception {
        MockHttpSession session = session("USER", true);
        respond("/auth/refresh", HttpStatus.OK, loginResponse("access-two", "refresh-two"), MediaType.APPLICATION_JSON);
        mvc.perform(get("/account").session(session)).andExpect(status().isOk());
        SessionAuth renewed = (SessionAuth) session.getAttribute(AuthSessionService.ATTRIBUTE);
        assertThat(renewed.accessToken()).isEqualTo("access-two");
        assertThat(renewed.refreshToken()).isEqualTo("refresh-two");
        MockHttpSession failed = session("USER", true);
        respond("/auth/refresh", HttpStatus.UNAUTHORIZED, "{\"status\":401}", MediaType.APPLICATION_PROBLEM_JSON);
        mvc.perform(get("/account").session(failed)).andExpect(redirectedUrl("/login?expired=1"));
        assertThat(failed.isInvalid()).isTrue();

        MockHttpSession unavailable = session("USER", true);
        respond("/auth/refresh", HttpStatus.SERVICE_UNAVAILABLE, "{}", MediaType.APPLICATION_PROBLEM_JSON);
        mvc.perform(get("/account").session(unavailable)).andExpect(redirectedUrl("/login?unavailable=1"));
        assertThat(unavailable.isInvalid()).isFalse();
    }

    @Test void protectedCallRetriesWithRotatedAccessToken() {
        MockHttpSession session = session("USER", false);
        stub.server.expect(requestTo("http://producer.test/api/v1/me/example"))
                .andExpect(header("Authorization", "Bearer access-one"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED).contentType(MediaType.APPLICATION_PROBLEM_JSON).body("{}"));
        stub.server.expect(requestTo("http://producer.test/api/v1/auth/refresh"))
                .andRespond(withStatus(HttpStatus.OK).contentType(MediaType.APPLICATION_JSON)
                        .body(loginResponse("access-two", "refresh-two")));
        stub.server.expect(requestTo("http://producer.test/api/v1/me/example"))
                .andExpect(header("Authorization", "Bearer access-two"))
                .andRespond(withStatus(HttpStatus.OK).contentType(MediaType.TEXT_PLAIN).body("ok"));
        String result = sessions.withAccess(session,
                token -> producer.getProtected("/me/example", token, String.class));
        assertThat(result).isEqualTo("ok");
        assertThat(((SessionAuth) session.getAttribute(AuthSessionService.ATTRIBUTE)).refreshToken()).isEqualTo("refresh-two");
        stub.server.verify();
    }

    @Test void logoutAndDeletionClearSessions() throws Exception {
        stub.server.expect(requestTo("http://producer.test/api/v1/auth/logout"))
                .andExpect(header("Authorization", "Bearer access-one"))
                .andRespond(withStatus(HttpStatus.NO_CONTENT));
        MockHttpSession logout = session("USER", false);
        mvc.perform(post("/logout").with(csrf()).session(logout)).andExpect(redirectedUrl("/login?logout=1"));
        assertThat(logout.isInvalid()).isTrue();
        respond("/me", HttpStatus.NO_CONTENT, "", MediaType.APPLICATION_JSON);
        MockHttpSession deletion = session("USER", false);
        mvc.perform(post("/account/delete").with(csrf()).session(deletion)).andExpect(redirectedUrl("/login?deleted=1"));
        assertThat(deletion.isInvalid()).isTrue();
    }

    @Test void problemMappingAndUnavailable() throws Exception {
        respond("/auth/register", HttpStatus.CONFLICT,
                "{\"status\":409,\"code\":\"USERNAME_GIA_UTILIZZATO\",\"detail\":\"private input\"}",
                MediaType.APPLICATION_PROBLEM_JSON);
        assertThatThrownBy(() -> producer.register(new RegistrationRequest("alice", "a@example.com", "password123")))
                .isInstanceOfSatisfying(ProducerException.class, ex -> {
                    assertThat(ex.status()).isEqualTo(409);
                    assertThat(ex.code()).isEqualTo("USERNAME_GIA_UTILIZZATO");
                    assertThat(ex.getMessage()).doesNotContain("private input");
                });
        respond("/auth/register", HttpStatus.SERVICE_UNAVAILABLE, "{}", MediaType.APPLICATION_PROBLEM_JSON);
        assertThatThrownBy(() -> producer.register(new RegistrationRequest("alice", "a@example.com", "password123")))
                .isInstanceOf(ProducerUnavailableException.class);
        respond("/auth/login", HttpStatus.SERVICE_UNAVAILABLE, "{}", MediaType.APPLICATION_PROBLEM_JSON);
        mvc.perform(post("/login").with(csrf()).param("username", "alice").param("password", "password123"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("temporarily unavailable")));
    }

    private MockHttpSession session(String role, boolean expired) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(AuthSessionService.ATTRIBUTE, new SessionAuth("access-one",
                Instant.now().plusSeconds(expired ? -60 : 600), "refresh-one",
                Instant.now().plusSeconds(3600), 7L, "alice", role));
        return session;
    }

    private String loginResponse(String access, String refresh) {
        return "{\"accessToken\":\"" + access + "\",\"tokenType\":\"Bearer\",\"expiresAt\":\""
                + Instant.now().plusSeconds(600) + "\",\"refreshToken\":\"" + refresh
                + "\",\"refreshExpiresAt\":\"" + Instant.now().plusSeconds(3600)
                + "\",\"userId\":7,\"username\":\"alice\",\"ruolo\":\"USER\"}";
    }

}
