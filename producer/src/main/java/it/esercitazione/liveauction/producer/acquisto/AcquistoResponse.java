package it.esercitazione.liveauction.producer.acquisto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record AcquistoResponse(UUID id, long prodottoId, int quantita, BigDecimal prezzoUnitario,
                               BigDecimal totale, String valuta, Instant acquistatoAt, boolean duplicato) {}
