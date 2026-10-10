package it.esercitazione.liveauction.consumer.auth;

import it.esercitazione.liveauction.consumer.dto.LoginResponse;
import java.io.Serializable;
import java.time.Instant;

public record SessionAuth(String accessToken, Instant expiresAt, String refreshToken,
                          Instant refreshExpiresAt, Long userId, String username, String role)
        implements Serializable {
    @Override
    public String toString() {
        return "SessionAuth[userId=" + userId + ", username=" + username + ", role=" + role + "]";
    }

    public static SessionAuth from(LoginResponse response) {
        if (response == null || !"Bearer".equals(response.tokenType())
                || response.accessToken() == null || response.accessToken().isBlank()
                || response.refreshToken() == null || response.refreshToken().isBlank()
                || response.expiresAt() == null || response.refreshExpiresAt() == null
                || response.userId() == null || response.username() == null
                || !("USER".equals(response.ruolo()) || "ADMIN".equals(response.ruolo()))) {
            throw new IllegalStateException("Invalid Producer authentication response");
        }
        return new SessionAuth(response.accessToken(), response.expiresAt(), response.refreshToken(),
                response.refreshExpiresAt(), response.userId(), response.username(), response.ruolo());
    }
}
