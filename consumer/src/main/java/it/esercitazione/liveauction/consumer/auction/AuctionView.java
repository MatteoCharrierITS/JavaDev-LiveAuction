package it.esercitazione.liveauction.consumer.auction;

import java.math.BigDecimal;
import java.time.Instant;

public record AuctionView(Long id, String stato, Product prodotto,
                          BigDecimal prezzoIniziale, BigDecimal offertaCorrente, long numeroOfferte,
                          Instant aperturaStanzaAt, Instant inizioAt, Instant fineAt) {
    public record Product(Long id, String nome) { }

    public boolean roomAvailableAt(Instant serverTime) {
        return id != null && serverTime != null && aperturaStanzaAt != null
                && !serverTime.isBefore(aperturaStanzaAt)
                && ("STANZA_APERTA".equals(stato) || "APERTA".equals(stato));
    }

    public String countdownLabel() {
        return switch (stato == null ? "" : stato) {
            case "PROGRAMMATA" -> "Room opens in";
            case "STANZA_APERTA" -> "Auction starts in";
            case "APERTA" -> "Ends in";
            default -> null;
        };
    }

    public Instant countdownTarget() {
        return switch (stato == null ? "" : stato) {
            case "PROGRAMMATA" -> aperturaStanzaAt;
            case "STANZA_APERTA" -> inizioAt;
            case "APERTA" -> fineAt;
            default -> null;
        };
    }
}
