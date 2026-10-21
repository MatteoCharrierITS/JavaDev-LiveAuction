package it.esercitazione.liveauction.producer.auth.requests;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Credenziali per aprire una sessione.")

public record LoginRequest(
        @NotBlank @Schema(description = "Username scelto in registrazione.", example = "mario.rossi") String username,
        @NotBlank @Schema(description = "Password in chiaro.", example = "Password123!") String password
) {
}
