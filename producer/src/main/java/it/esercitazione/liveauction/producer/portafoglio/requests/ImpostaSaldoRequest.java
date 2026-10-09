package it.esercitazione.liveauction.producer.portafoglio.requests;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record ImpostaSaldoRequest(
        @NotNull @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal saldoTotale) {}
