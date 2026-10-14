package it.esercitazione.liveauction.producer.acquisto;

import jakarta.validation.constraints.*;
import java.util.UUID;

public record AcquistoRequest(@NotNull UUID clientPurchaseId,
        @com.fasterxml.jackson.databind.annotation.JsonDeserialize(using=QuantitaIntera.class)
        @NotNull @Min(1) @Max(100) Integer quantita) {
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
