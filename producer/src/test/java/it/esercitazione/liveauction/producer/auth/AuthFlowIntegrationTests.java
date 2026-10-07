package it.esercitazione.liveauction.producer.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import it.esercitazione.liveauction.producer.auth.services.EliminazioneUtenteService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {"spring.docker.compose.enabled=false", "app.aste.scheduler.enabled=false"})
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "RUN_DB_TESTS", matches = "true")
class AuthFlowIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired JwtDecoder jwtDecoder;
    @Autowired EliminazioneUtenteService eliminazioneUtenteService;

    @Test
    void registrationCreatesWalletAndLoginEnforcesRoles() throws Exception {
        String username = "test_" + UUID.randomUUID().toString().substring(0, 8);
        String password = "TestPassword123!";
        String body = json.writeValueAsString(new Registration(username,
                username + "@example.com", password));

        String registrationBody = mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value(username))
                .andExpect(jsonPath("$.ruolo").value("USER"))
                .andReturn().getResponse().getContentAsString();
        long userId = json.readTree(registrationBody).get("id").asLong();
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM portafogli WHERE utente_id = ?", Integer.class, userId)).isEqualTo(1);
        String hash = jdbc.queryForObject(
                "SELECT password_hash FROM utenti WHERE id = ?", String.class, userId);
        assertThat(hash).isNotEqualTo(password);
        assertThat(passwordEncoder.matches(password, hash)).isTrue();

        mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new Login(username, "wrong-password"))))
                .andExpect(status().isUnauthorized());

        String loginBody = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new Login(username, password))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andReturn().getResponse().getContentAsString();
        JsonNode login = json.readTree(loginBody);
        String token = login.get("accessToken").asText();
        String refreshToken = login.get("refreshToken").asText();
        assertThat(token).isNotBlank();
        assertThat(refreshToken).isNotBlank();

        String refreshBody = mvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new Refresh(refreshToken))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode refreshed = json.readTree(refreshBody);
        String rotatedToken = refreshed.get("refreshToken").asText();
        assertThat(rotatedToken).isNotEqualTo(refreshToken);
        mvc.perform(get("/api/v1/me/portafoglio")
                        .header("Authorization", "Bearer " + refreshed.get("accessToken").asText()))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new Refresh(refreshToken))))
                .andExpect(status().isUnauthorized());

        mvc.perform(post("/api/v1/admin/aste")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/admin/aste"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/logout"))
                .andExpect(status().isUnauthorized());

        mvc.perform(get("/api/v1/me/portafoglio")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/auth/logout")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/me/portafoglio")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new Refresh(rotatedToken))))
                .andExpect(status().isUnauthorized());

        String activeToken = json.readTree(mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new Login(username, password))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()).get("accessToken").asText();
        jdbc.update("UPDATE utenti SET attivo = FALSE WHERE id = ?", userId);
        mvc.perform(get("/api/v1/me/portafoglio")
                        .header("Authorization", "Bearer " + activeToken))
                .andExpect(status().isUnauthorized());

        String adminUsername = "admin_" + UUID.randomUUID().toString().substring(0, 8);
        String adminRegistration = json.writeValueAsString(new Registration(adminUsername,
                adminUsername + "@example.com", password));
        mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(adminRegistration))
                .andExpect(status().isCreated());
        jdbc.update("UPDATE utenti SET ruolo = 'ADMIN' WHERE username = ?", adminUsername);
        String adminLoginBody = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new Login(adminUsername, password))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String adminToken = json.readTree(adminLoginBody).get("accessToken").asText();
        // L'ADMIN raggiunge il controller: senza il body obbligatorio riceve 400, non 403.
        mvc.perform(post("/api/v1/admin/aste")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deleteAccountAnonymizesUserAndRevokesAllSessions() throws Exception {
        String username = "erase_" + UUID.randomUUID().toString().substring(0, 8);
        String email = username + "@example.com";
        String password = "TestPassword123!";
        String registration = json.writeValueAsString(new Registration(username, email, password));
        long userId = json.readTree(mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(registration))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).get("id").asLong();
        String originalHash = jdbc.queryForObject(
                "SELECT password_hash FROM utenti WHERE id = ?", String.class, userId);

        String loginRequest = json.writeValueAsString(new Login(username, password));
        JsonNode firstLogin = json.readTree(mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON).content(loginRequest))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        JsonNode secondLogin = json.readTree(mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON).content(loginRequest))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new JwtAuthenticationToken(
                jwtDecoder.decode(firstLogin.get("accessToken").asText())));
        SecurityContextHolder.setContext(context);
        try {
            assertThatThrownBy(() -> eliminazioneUtenteService.elimina(userId + 1))
                    .isInstanceOf(AccessDeniedException.class);
        } finally {
            SecurityContextHolder.clearContext();
        }

        mvc.perform(delete("/api/v1/me"))
                .andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/v1/me")
                        .header("Authorization", "Bearer " + firstLogin.get("accessToken").asText()))
                .andExpect(status().isNoContent());

        assertThat(jdbc.queryForObject("SELECT attivo FROM utenti WHERE id = ?", Boolean.class, userId))
                .isFalse();
        String anonymousUsername = jdbc.queryForObject(
                "SELECT username FROM utenti WHERE id = ?", String.class, userId);
        String anonymousEmail = jdbc.queryForObject(
                "SELECT email FROM utenti WHERE id = ?", String.class, userId);
        String newHash = jdbc.queryForObject(
                "SELECT password_hash FROM utenti WHERE id = ?", String.class, userId);
        assertThat(anonymousUsername).startsWith("deleted_").isNotEqualTo(username);
        assertThat(anonymousEmail).endsWith("@example.invalid").isNotEqualTo(email);
        assertThat(newHash).isNotEqualTo(originalHash);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM portafogli WHERE utente_id = ?", Integer.class, userId)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM auth_sessions WHERE utente_id = ? AND revoked_at IS NULL",
                Integer.class, userId)).isZero();

        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON).content(loginRequest))
                .andExpect(status().isUnauthorized());
        for (JsonNode login : new JsonNode[]{firstLogin, secondLogin}) {
            mvc.perform(get("/api/v1/me/portafoglio")
                            .header("Authorization", "Bearer " + login.get("accessToken").asText()))
                    .andExpect(status().isUnauthorized());
            mvc.perform(post("/api/v1/auth/refresh")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json.writeValueAsString(new Refresh(login.get("refreshToken").asText()))))
                    .andExpect(status().isUnauthorized());
        }
        mvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(registration))
                .andExpect(status().isCreated());
    }

    private record Registration(String username, String email, String password) {}
    private record Login(String username, String password) {}
    private record Refresh(String refreshToken) {}
}
