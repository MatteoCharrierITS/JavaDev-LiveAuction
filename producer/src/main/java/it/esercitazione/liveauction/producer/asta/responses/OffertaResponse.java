package it.esercitazione.liveauction.producer.asta.responses;

import java.math.BigDecimal;
import java.util.UUID;

public record OffertaResponse(
        long offertaId, UUID clientBidId, BigDecimal importo,
        boolean duplicata, boolean ritirata, boolean snapshotRequired,
        StatoOfferteResponse stato
) {}
