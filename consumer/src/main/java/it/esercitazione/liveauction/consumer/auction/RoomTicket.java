package it.esercitazione.liveauction.consumer.auction;

import java.time.Instant;

public record RoomTicket(String ticket, Instant expiresAt) {
    @Override public String toString() { return "RoomTicket[redacted]"; }
}
