package it.esercitazione.liveauction.consumer.dto;

import java.time.Instant;

public record LoginResponse(String accessToken, String tokenType, Instant expiresAt,
                            String refreshToken, Instant refreshExpiresAt, Long userId,
                            String username, String ruolo) {
    @Override
    public String toString() {
        return "LoginResponse[userId=" + userId + ", username=" + username + ", ruolo=" + ruolo + "]";
    }
}
