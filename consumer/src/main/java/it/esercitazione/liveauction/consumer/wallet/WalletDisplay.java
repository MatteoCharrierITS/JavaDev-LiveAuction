package it.esercitazione.liveauction.consumer.wallet;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public final class WalletDisplay {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
            .withZone(ZoneId.of("Europe/Rome"));

    public WalletDisplay() {}

    public String amount(BigDecimal value) {
        NumberFormat format = NumberFormat.getNumberInstance(Locale.ITALY);
        format.setMinimumFractionDigits(2);
        format.setMaximumFractionDigits(2);
        return format.format(value);
    }

    public String date(Instant instant) { return DATE.format(instant); }

    public String type(String type) {
        return switch (type) {
            case "IMPOSTAZIONE_SALDO" -> "Balance set";
            case "RISERVA_OFFERTA" -> "Bid reserved";
            case "RILASCIO_OFFERTA" -> "Bid reserve released";
            case "PAGAMENTO_ASTA" -> "Auction payment";
            case "INCASSO_ASTA" -> "Auction proceeds";
            case "ACQUISTO_FISSO" -> "Fixed-price purchase";
            default -> type.replace('_', ' ');
        };
    }
}
