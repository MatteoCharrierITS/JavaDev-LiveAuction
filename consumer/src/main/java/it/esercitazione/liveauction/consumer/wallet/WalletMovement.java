package it.esercitazione.liveauction.consumer.wallet;

import java.math.BigDecimal;
import java.time.Instant;

public record WalletMovement(long id, Long astaId, String tipo, BigDecimal importo,
                             BigDecimal saldoTotaleDopo, BigDecimal saldoRiservatoDopo,
                             Instant dataMovimento) {}
