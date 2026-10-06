package it.esercitazione.liveauction.producer.websocket;

import it.esercitazione.liveauction.producer.asta.AccessoStanzaService;
import it.esercitazione.liveauction.producer.auth.services.WebSocketTicketService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

import java.security.Principal;
import java.time.Instant;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class AstaStompAuthorizationInterceptor implements ChannelInterceptor {
    private static final Pattern TOPIC = Pattern.compile("^/topic/aste/(\\d+)$");
    private static final Pattern COMMAND = Pattern.compile("^/app/aste/(\\d+)/(join|offerte)$");

    private final WebSocketTicketService tickets;
    private final AccessoStanzaService accessoStanza;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }
        StompCommand command = accessor.getCommand();
        if (command == StompCommand.DISCONNECT || command == StompCommand.UNSUBSCRIBE) {
            return message;
        }
        Principal principal = accessor.getUser();
        if (!(principal instanceof WebSocketIdentity identity) || !tickets.isActive(identity)) {
            throw new AccessDeniedException("Sessione WebSocket non autorizzata");
        }
        if (command == StompCommand.CONNECT || command == StompCommand.STOMP) {
            return message;
        }
        if (command == StompCommand.SUBSCRIBE) {
            if ("/user/queue/aste".equals(accessor.getDestination())) {
                return message;
            }
            authorizeAuction(TOPIC.matcher(accessor.getDestination() == null ? "" : accessor.getDestination()),
                    identity);
            return message;
        }
        if (command == StompCommand.SEND) {
            authorizeAuction(COMMAND.matcher(accessor.getDestination() == null ? "" : accessor.getDestination()),
                    identity);
            return message;
        }
        throw new AccessDeniedException("Comando STOMP non consentito");
    }

    private void authorizeAuction(Matcher destination, WebSocketIdentity identity) {
        if (!destination.matches() || !Long.toString(identity.auctionId()).equals(destination.group(1))) {
            throw new AccessDeniedException("Destinazione STOMP non consentita");
        }
        accessoStanza.verificaAccesso(identity.auctionId(), Instant.now());
    }
}
