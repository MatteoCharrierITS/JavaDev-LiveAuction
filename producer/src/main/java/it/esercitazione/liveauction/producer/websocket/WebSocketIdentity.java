package it.esercitazione.liveauction.producer.websocket;

import java.security.Principal;
import java.util.UUID;

public record WebSocketIdentity(long userId, UUID authSessionId, long auctionId) implements Principal {
    @Override
    public String getName() {
        return Long.toString(userId);
    }
}
