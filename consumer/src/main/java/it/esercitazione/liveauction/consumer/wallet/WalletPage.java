package it.esercitazione.liveauction.consumer.wallet;

import java.math.BigDecimal;
import java.util.List;

public record WalletPage(BigDecimal saldoTotale, BigDecimal saldoRiservato,
                         BigDecimal saldoDisponibile, String valuta,
                         List<WalletMovement> movimenti, int page, int size,
                         long totalElements, long totalPages) {}
