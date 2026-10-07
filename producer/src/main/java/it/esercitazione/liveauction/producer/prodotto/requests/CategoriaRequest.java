package it.esercitazione.liveauction.producer.prodotto.requests;

import jakarta.validation.constraints.*;

public record CategoriaRequest(
        @NotBlank @Size(max = 100) String nome,
        @NotBlank @Size(max = 100)
        @Pattern(regexp = "[a-z0-9]+(-[a-z0-9]+)*",
                message = "deve contenere solo lettere minuscole, cifre e trattini") String slug,
        // Facoltativo: in creazione vale true, in modifica conserva il valore attuale.
        Boolean attiva
) {
}
