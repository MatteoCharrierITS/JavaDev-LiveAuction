package it.esercitazione.liveauction.producer.asta.events;

import it.esercitazione.liveauction.producer.asta.models.Stato;

import java.time.Instant;

/** Snapshot immutabile della transizione, pubblicato solo dopo il commit. */
public record AstaTransizioneEvent(
        Tipo type,
        Long auctionId,
        long sequence,
        Instant serverTime,
        Stato stato,
        Instant aperturaStanzaAt,
        Instant inizioAt,
        Instant fineAt
) {
    public enum Tipo {
        ROOM_OPENED,
        AUCTION_STARTED
    }
}
