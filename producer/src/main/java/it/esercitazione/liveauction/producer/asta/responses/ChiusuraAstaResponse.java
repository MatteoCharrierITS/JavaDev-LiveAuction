package it.esercitazione.liveauction.producer.asta.responses;

import java.math.BigDecimal;
import java.time.Instant;

public record ChiusuraAstaResponse(
        long astaId, boolean conclusa, String stato, Long vincitoreId,
        BigDecimal prezzoFinale, Instant chiusaAt, long sequence
) {}
