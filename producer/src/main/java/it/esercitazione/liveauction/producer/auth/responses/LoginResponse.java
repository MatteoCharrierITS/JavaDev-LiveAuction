package it.esercitazione.liveauction.producer.auth.responses;

import io.swagger.v3.oas.annotations.media.Schema;
import it.esercitazione.liveauction.producer.auth.models.Ruolo;

import java.time.Instant;

@Schema(description = "Token di sessione e dati dell'utente.")

public record LoginResponse(
        @Schema(description = "JWT da inviare come `Authorization: Bearer <accessToken>` (30 minuti). Incollalo in Authorize.") String accessToken,
        @Schema(description = "Sempre `Bearer`.", example = "Bearer") String tokenType,
        @Schema(description = "Scadenza dell'access token (UTC).", example = "2026-10-21T10:30:00Z") Instant expiresAt,
        @Schema(description = "Token per `POST /api/v1/auth/refresh` (7 giorni).") String refreshToken,
        @Schema(description = "Scadenza della sessione e del refresh token (UTC).", example = "2026-10-28T10:00:00Z") Instant refreshExpiresAt,
        @Schema(description = "ID dell'utente.", example = "9") Long userId,
        @Schema(description = "Username.", example = "mario.rossi") String username,
        @Schema(description = "Ruolo: `USER` o `ADMIN`.", example = "USER") Ruolo ruolo
) {
}
