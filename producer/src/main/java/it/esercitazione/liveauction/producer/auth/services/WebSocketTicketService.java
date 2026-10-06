package it.esercitazione.liveauction.producer.auth.services;

import it.esercitazione.liveauction.producer.asta.AccessoStanzaService;
import it.esercitazione.liveauction.producer.websocket.WebSocketIdentity;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.web.ErrorResponseException;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WebSocketTicketService {
    private final AccessoStanzaService accessoStanza;
    private final WebSocketTicketStore tickets;
    private final JdbcTemplate jdbc;

    @PreAuthorize("hasRole('USER')")
    public WebSocketTicketStore.IssuedTicket issue(long auctionId, JwtAuthenticationToken authentication) {
        Instant now = Instant.now();
        accessoStanza.verificaAccesso(auctionId, now);
        long userId = Long.parseLong(authentication.getToken().getSubject());
        UUID authSessionId = UUID.fromString(authentication.getToken().getClaimAsString("sid"));
        return tickets.issue(userId, authSessionId, auctionId, now);
    }

    public Optional<WebSocketIdentity> consume(String value) {
        Optional<WebSocketTicketStore.TicketData> ticket = tickets.consume(value, Instant.now());
        if (ticket.isEmpty()) {
            return Optional.empty();
        }
        WebSocketTicketStore.TicketData data = ticket.get();
        WebSocketIdentity identity = new WebSocketIdentity(data.userId(), data.authSessionId(),
                data.auctionId());
        if (!isActive(identity)) {
            return Optional.empty();
        }
        try {
            accessoStanza.verificaAccesso(identity.auctionId(), Instant.now());
        } catch (ErrorResponseException exception) {
            return Optional.empty();
        }
        return Optional.of(identity);
    }

    public boolean isActive(WebSocketIdentity identity) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM auth_sessions s JOIN utenti u ON u.id = s.utente_id
                WHERE s.id = ? AND s.utente_id = ? AND s.revoked_at IS NULL
                  AND s.expires_at > CURRENT_TIMESTAMP AND u.attivo = TRUE AND u.ruolo = 'USER'
                """, Integer.class, identity.authSessionId(), identity.userId());
        return count != null && count > 0;
    }
}
