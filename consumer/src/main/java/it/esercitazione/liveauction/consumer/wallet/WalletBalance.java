package it.esercitazione.liveauction.consumer.wallet;

import java.math.BigDecimal;

public record WalletBalance(BigDecimal saldoTotale, BigDecimal saldoRiservato,
                            BigDecimal saldoDisponibile, String valuta) {}
