package it.esercitazione.liveauction.producer.asta.requests;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Schema(description = "Programmazione di un'asta: orario locale, fuso e prezzo iniziale.")

public record ProgrammaAstaRequest(
        @NotNull @Positive @Schema(description = "ID di un prodotto `astabile` con almeno una unità disponibile.", example = "1") Long prodottoId,
        @NotNull @Schema(description = "Inizio dell'asta in ora locale, senza offset, nel fuso `timeZone`. Deve essere nel futuro.", example = "2027-06-15T18:30:00") LocalDateTime inizioLocale,
        @NotBlank @Pattern(regexp = "Europe/Rome") @Schema(description = "Fuso di `inizioLocale`: l'unico ammesso è `Europe/Rome`.", example = "Europe/Rome") String timeZone,
        @NotNull @Positive @Digits(integer = 10, fraction = 2) @Schema(description = "Prezzo base in CRD, positivo, max 10 cifre intere e 2 decimali.", example = "500.00") BigDecimal prezzoIniziale
) {
}
