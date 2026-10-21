package it.esercitazione.liveauction.producer.asta.requests;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.util.UUID;

@Schema(description = "Comando STOMP `PLACE_BID` (solo WebSocket, non è una rotta REST).")

public record OffertaRequest(
        @NotBlank @Pattern(regexp = "PLACE_BID") @Schema(description = "Sempre `PLACE_BID`.", example = "PLACE_BID") String type,
        @NotNull @Schema(description = "UUID scelto dal client per l'idempotenza.", example = "5ab0d96e-c7b0-42d1-a74b-97180d9849b8") UUID clientBidId,
        @NotNull @Positive @Digits(integer = 10, fraction = 2) @Schema(description = "Importo offerto in CRD.", example = "630.00") BigDecimal importo,
        @NotNull @PositiveOrZero @Schema(description = "Ultima `sequence` nota al client.", example = "8") Long knownSequence
) {}
