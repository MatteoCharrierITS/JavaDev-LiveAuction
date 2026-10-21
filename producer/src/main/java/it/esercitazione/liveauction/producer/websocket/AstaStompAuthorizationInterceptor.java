package it.esercitazione.liveauction.producer.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.esercitazione.liveauction.producer.asta.AccessoStanzaService;
import it.esercitazione.liveauction.producer.auth.services.WebSocketTicketService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.security.Principal;
import java.time.Instant;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class AstaStompAuthorizationInterceptor implements ChannelInterceptor {
    private static final Pattern TOPIC = Pattern.compile("^/topic/aste/(\\d+)$");
    private static final Pattern COMMAND = Pattern.compile("^/app/aste/(\\d+)/(join|offerte)$");

    private final WebSocketTicketService tickets;
    private final AccessoStanzaService accessoStanza;
    // Lazy: il template del broker dipende dalla configurazione che registra questo interceptor.
    private final ObjectProvider<SimpMessagingTemplate> messaging;
    private final ObjectMapper json;

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
            Matcher destination = COMMAND.matcher(accessor.getDestination() == null ? "" : accessor.getDestination());
            authorizeAuction(destination, identity);
            if (identity.osservatore() && "offerte".equals(destination.group(2))) {
                rifiutaOffertaOsservatore(message, accessor, identity);
                return null;
            }
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

    /**
     * L'ADMIN osserva la stanza in sola lettura: il comando non raggiunge il controller e la sola
     * sessione che l'ha inviato riceve un BID_REJECTED privato. Con l'ordine di ricezione preservato
     * un'eccezione qui verrebbe soltanto registrata nel log, senza alcuna risposta al client.
     */
    private void rifiutaOffertaOsservatore(Message<?> message, StompHeaderAccessor accessor,
                                           WebSocketIdentity identity) {
        SimpMessageHeaderAccessor headers = SimpMessageHeaderAccessor.create(SimpMessageType.MESSAGE);
        headers.setSessionId(accessor.getSessionId());
        headers.setLeaveMutable(true);
        messaging.getObject().convertAndSendToUser(identity.getName(), "/queue/aste",
                new AstaOffertaController.EsitoOfferta("BID_REJECTED", clientBidId(message),
                        "OPERAZIONE_NON_CONSENTITA", "Un amministratore osserva la stanza senza offrire",
                        false, false, false, null),
                headers.getMessageHeaders());
    }

    private UUID clientBidId(Message<?> message) {
        try {
            String id = (message.getPayload() instanceof byte[] bytes ? json.readTree(bytes)
                    : json.readTree(message.getPayload().toString())).path("clientBidId").asText();
            return UUID.fromString(id);
        } catch (IOException | IllegalArgumentException | NullPointerException exception) {
            return null;
        }
    }
}
