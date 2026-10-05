package it.esercitazione.liveauction.producer.websocket;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.List;

@Controller
@RequiredArgsConstructor
public class AstaPresenceController {
    private final PresenceRegistry presence;
    private final JdbcTemplate jdbc;
    private final SimpMessagingTemplate messaging;

    @MessageMapping("/aste/{id}/join")
    @SendToUser(value = "/queue/aste", broadcast = false)
    public PresenceRegistry.PresenceSnapshot join(@DestinationVariable long id,
                                                  Principal principal,
                                                  SimpMessageHeaderAccessor headers) {
        if (!(principal instanceof WebSocketIdentity identity) || identity.auctionId() != id
                || headers.getSessionId() == null) {
            throw new AccessDeniedException("Ingresso stanza non autorizzato");
        }
        List<String> usernames = jdbc.query("SELECT username FROM utenti WHERE id = ? AND attivo = TRUE",
                (rs, row) -> rs.getString("username"), identity.userId());
        if (usernames.isEmpty()) {
            throw new AccessDeniedException("Utente non attivo");
        }
        PresenceRegistry.JoinResult result = presence.join(id, identity.userId(),
                mask(usernames.getFirst()), headers.getSessionId());
        result.change().ifPresent(event -> messaging.convertAndSend("/topic/aste/" + id, event));
        return result.snapshot();
    }

    private static String mask(String username) {
        if (username.length() == 1) {
            return username + "***";
        }
        return username.charAt(0) + "***" + username.charAt(username.length() - 1);
    }
}
