package it.esercitazione.liveauction.producer.asta.requests;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.util.UUID;

public record OffertaRequest(
        @NotBlank @Pattern(regexp = "PLACE_BID") String type,
        @NotNull UUID clientBidId,
        @NotNull @Positive @Digits(integer = 10, fraction = 2) BigDecimal importo,
        @NotNull @PositiveOrZero Long knownSequence
) {}
