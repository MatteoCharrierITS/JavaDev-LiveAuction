package it.esercitazione.liveauction.producer.prodotto.requests;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

@Schema(description = "Dati di una categoria (creazione e modifica).")

public record CategoriaRequest(
        @NotBlank @Size(max = 100) @Schema(description = "Nome univoco, max 100 caratteri.", example = "Informatica") String nome,
        @NotBlank @Size(max = 100)
        @Pattern(regexp = "[a-z0-9]+(-[a-z0-9]+)*",
                message = "deve contenere solo lettere minuscole, cifre e trattini") @Schema(description = "Slug univoco: lettere minuscole, cifre e trattini separatori, max 100 caratteri.", example = "informatica") String slug,
        // Facoltativo: in creazione vale true, in modifica conserva il valore attuale.
        @Schema(description = "Facoltativo: true in creazione se omesso; in modifica omesso conserva il valore corrente.", example = "true") Boolean attiva
) {
}
