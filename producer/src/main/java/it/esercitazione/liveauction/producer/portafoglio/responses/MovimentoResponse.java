package it.esercitazione.liveauction.producer.portafoglio.responses;

import it.esercitazione.liveauction.producer.portafoglio.model.TipoMovimento;
import java.math.BigDecimal;
import java.time.Instant;

public record MovimentoResponse(long id, Long astaId, TipoMovimento tipo, BigDecimal importo,
                                BigDecimal saldoTotaleDopo, BigDecimal saldoRiservatoDopo,
                                Instant dataMovimento) {}
