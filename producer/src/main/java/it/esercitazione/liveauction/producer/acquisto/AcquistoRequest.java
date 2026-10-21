package it.esercitazione.liveauction.producer.acquisto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import java.util.UUID;

@Schema(description = "Richiesta di acquisto a prezzo fisso.")

public record AcquistoRequest(@NotNull @Schema(description = "UUID scelto dal client: ripeterlo con gli stessi dati è idempotente (200 `duplicato: true`).", example = "d14995b2-c122-4b80-91f9-d1db6d152df4") UUID clientPurchaseId,
        @com.fasterxml.jackson.databind.annotation.JsonDeserialize(using=QuantitaIntera.class)
        @NotNull @Min(1) @Max(100) @Schema(description = "Intero JSON da 1 a 100.", example = "1") Integer quantita) {
    public static class QuantitaIntera extends com.fasterxml.jackson.databind.JsonDeserializer<Integer> {
        @Override public Integer deserialize(com.fasterxml.jackson.core.JsonParser parser,
                com.fasterxml.jackson.databind.DeserializationContext context) throws java.io.IOException {
            if(!parser.hasToken(com.fasterxml.jackson.core.JsonToken.VALUE_NUMBER_INT)) {
                return (Integer)context.handleUnexpectedToken(Integer.class,parser);
            }
            return parser.getIntValue();
        }
    }
}
