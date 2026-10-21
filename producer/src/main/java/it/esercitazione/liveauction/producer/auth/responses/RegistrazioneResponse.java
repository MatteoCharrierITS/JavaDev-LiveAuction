package it.esercitazione.liveauction.producer.auth.responses;

import io.swagger.v3.oas.annotations.media.Schema;
import it.esercitazione.liveauction.producer.auth.models.Ruolo;

@Schema(description = "Utente appena registrato (senza password).")

public record RegistrazioneResponse (
        @Schema(description = "ID dell'utente.", example = "9") Long id,
        @Schema(description = "Username.", example = "mario.rossi") String username,
        @Schema(description = "Email.", example = "mario.rossi@example.com") String email,
        @Schema(description = "Ruolo assegnato: sempre `USER`.", example = "USER") Ruolo ruolo
) {
}
