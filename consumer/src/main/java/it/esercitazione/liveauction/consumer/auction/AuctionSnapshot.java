package it.esercitazione.liveauction.consumer.auction;

import java.math.BigDecimal;
import java.time.Instant;

public record AuctionSnapshot(Long id, String stato, AuctionView.Product prodotto,
        BigDecimal prezzoIniziale, BigDecimal incrementoMinimo, BigDecimal offertaCorrente,
        Participant migliorOfferente, long numeroOfferte, Instant aperturaStanzaAt,
        Instant inizioAt, Instant fineAt, Instant serverTime, boolean offerteConsentite,
        long sequence, Participant vincitore, BigDecimal prezzoFinale, Instant chiusaAt) {
    public record Participant(Long id, String displayName) {}
}
