package it.esercitazione.liveauction.producer.auth.requests;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

@Schema(description = "Dati per creare un nuovo utente con ruolo USER. Il credito iniziale è deciso dal server.")

public record RegistrazioneRequest(
        @NotBlank @Size(min = 3, max = 50) @Schema(description = "Username univoco, 3-50 caratteri.", example = "mario.rossi") String username,
        @NotBlank @Email @Size(max = 200) @Schema(description = "Indirizzo email univoco, max 200 caratteri.", example = "mario.rossi@example.com") String email,
        @NotBlank @Size(min = 8, max = 72) @Schema(description = "Password, 8-72 caratteri.", example = "Password123!") String password
) {
}
