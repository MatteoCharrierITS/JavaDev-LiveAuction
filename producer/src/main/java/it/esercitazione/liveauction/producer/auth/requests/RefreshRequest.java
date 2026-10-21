package it.esercitazione.liveauction.producer.auth.requests;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Refresh token ottenuto dal login o dal rinnovo precedente.")

public record RefreshRequest(@NotBlank @Schema(description = "Refresh token, valido 7 giorni e monouso: ogni rinnovo lo sostituisce.", example = "incolla-qui-il-refreshToken-del-login") String refreshToken) {
}
