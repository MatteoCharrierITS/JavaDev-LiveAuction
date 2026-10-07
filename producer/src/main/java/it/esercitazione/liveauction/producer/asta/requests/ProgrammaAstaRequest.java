package it.esercitazione.liveauction.producer.asta.requests;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ProgrammaAstaRequest(
        @NotNull @Positive Long prodottoId,
        @NotNull LocalDateTime inizioLocale,
        @NotBlank @Pattern(regexp = "Europe/Rome") String timeZone,
        @NotNull @Positive @Digits(integer = 10, fraction = 2) BigDecimal prezzoIniziale
) {
}
