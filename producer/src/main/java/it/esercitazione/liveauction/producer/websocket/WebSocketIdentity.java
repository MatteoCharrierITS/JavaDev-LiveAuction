package it.esercitazione.liveauction.producer.websocket;

import it.esercitazione.liveauction.producer.auth.models.Ruolo;

import java.security.Principal;
import java.util.Objects;
import java.util.UUID;

/**
 * Identità della connessione STOMP ricavata dal ticket. Il ruolo è quello che l'utente aveva
 * all'emissione del ticket e viene ricontrollato sul database a ogni comando: un ADMIN entra
 * nella stanza come osservatore in sola lettura.
 */
public record WebSocketIdentity(long userId, UUID authSessionId, long auctionId, Ruolo ruolo)
        implements Principal {
    public WebSocketIdentity {
        Objects.requireNonNull(ruolo, "ruolo");
    }

    /** Identità di un offerente: mantiene la forma usata prima dell'introduzione del ruolo. */
    public WebSocketIdentity(long userId, UUID authSessionId, long auctionId) {
        this(userId, authSessionId, auctionId, Ruolo.USER);
    }

    public boolean osservatore() {
        return ruolo == Ruolo.ADMIN;
    }

    @Override
    public String getName() {
        return Long.toString(userId);
    }
}
