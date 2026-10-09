package it.esercitazione.liveauction.producer.portafoglio.responses;

import java.math.BigDecimal;

public record SaldoResponse(BigDecimal saldoTotale, BigDecimal saldoRiservato,
                            BigDecimal saldoDisponibile, String valuta) {}
