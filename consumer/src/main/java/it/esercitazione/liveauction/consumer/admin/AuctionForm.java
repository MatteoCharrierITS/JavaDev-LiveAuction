package it.esercitazione.liveauction.consumer.admin;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.format.annotation.DateTimeFormat;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public record AuctionForm(@NotNull @Positive Long prodottoId,
        @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime inizioLocale,
        @NotNull @Positive @Digits(integer = 10, fraction = 2) BigDecimal prezzoIniziale) {
    public AuctionRequest request() { return new AuctionRequest(prodottoId,
            inizioLocale.format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")), "Europe/Rome", prezzoIniziale); }
    public record AuctionRequest(Long prodottoId, String inizioLocale, String timeZone,
                                 BigDecimal prezzoIniziale) {}
}
