package it.esercitazione.liveauction.producer.portafoglio.config;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;

@Validated
@ConfigurationProperties(prefix = "app.portafoglio")
public record PortafoglioProperties(
        @NotNull @DecimalMin("0.00") @DecimalMax("9999999999.99")
        @Digits(integer = 10, fraction = 2) BigDecimal saldoIniziale) {
}
