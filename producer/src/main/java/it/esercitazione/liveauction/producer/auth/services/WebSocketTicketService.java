package it.esercitazione.liveauction.producer.auth.services;

import it.esercitazione.liveauction.producer.asta.AccessoStanzaService;
import it.esercitazione.liveauction.producer.auth.models.Ruolo;
import it.esercitazione.liveauction.producer.websocket.WebSocketIdentity;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
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

    /**
     * Emette il ticket della stanza. Un USER entra come offerente; un ADMIN come osservatore in
     * sola lettura: il ruolo resta nel ticket lato server e limita i comandi STOMP consentiti.
     */
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public WebSocketTicketStore.IssuedTicket issue(long auctionId, JwtAuthenticationToken authentication) {
        Instant now = Instant.now();
        accessoStanza.verificaAccesso(auctionId, now);
        long userId = Long.parseLong(authentication.getToken().getSubject());
        UUID authSessionId = UUID.fromString(authentication.getToken().getClaimAsString("sid"));
        return tickets.issue(userId, authSessionId, auctionId, ruolo(authentication), now);
    }

    private static Ruolo ruolo(JwtAuthenticationToken authentication) {
        // Le authority arrivano dal database (jwtAuthenticationConverter), non dai claim del token.
        boolean admin = authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
        boolean user = authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_USER".equals(authority.getAuthority()));
        if (admin == user) {
            throw new AccessDeniedException("Ruolo non ammesso nella stanza");
        }
        return admin ? Ruolo.ADMIN : Ruolo.USER;
    }

    public Optional<WebSocketIdentity> consume(String value) {
        Optional<WebSocketTicketStore.TicketData> ticket = tickets.consume(value, Instant.now());
        if (ticket.isEmpty()) {
            return Optional.empty();
        }
        WebSocketTicketStore.TicketData data = ticket.get();
        WebSocketIdentity identity = new WebSocketIdentity(data.userId(), data.authSessionId(),
                data.auctionId(), data.ruolo());
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

    /**
     * Sessione ancora valida e ruolo invariato rispetto al ticket: un cambio di ruolo chiude
     * l'accesso invece di trasformare un osservatore in offerente o viceversa.
     */
    public boolean isActive(WebSocketIdentity identity) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM auth_sessions s JOIN utenti u ON u.id = s.utente_id
                WHERE s.id = ? AND s.utente_id = ? AND s.revoked_at IS NULL
                  AND s.expires_at > CURRENT_TIMESTAMP AND u.attivo = TRUE
                  AND u.ruolo = ?
                """, Integer.class, identity.authSessionId(), identity.userId(), identity.ruolo().name());
        return count != null && count > 0;
    }
}
