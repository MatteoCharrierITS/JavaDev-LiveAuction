package it.esercitazione.liveauction.producer.websocket;

import it.esercitazione.liveauction.producer.asta.AccessoStanzaService;
import it.esercitazione.liveauction.producer.auth.services.WebSocketTicketService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.access.AccessDeniedException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AstaStompAuthorizationInterceptorTests {
    private final WebSocketTicketService tickets = mock(WebSocketTicketService.class);
    private final AccessoStanzaService accessoStanza = mock(AccessoStanzaService.class);
    private final AstaStompAuthorizationInterceptor interceptor =
            new AstaStompAuthorizationInterceptor(tickets, accessoStanza);
    private final WebSocketIdentity identity = new WebSocketIdentity(7, UUID.randomUUID(), 42);

    @BeforeEach
    void activeSession() {
        when(tickets.isActive(identity)).thenReturn(true);
    }

    @Test
    void permitsOnlyTheAuctionBoundToTheTicket() {
        assertThatCode(() -> authorize(StompCommand.SUBSCRIBE, "/topic/aste/42"))
                .doesNotThrowAnyException();
        assertThatCode(() -> authorize(StompCommand.SEND, "/app/aste/42/join"))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> authorize(StompCommand.SUBSCRIBE, "/topic/aste/43"))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> authorize(StompCommand.SEND, "/app/aste/43/offerte"))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void rejectsPublishingToBrokerOrSubscribingToAnotherQueue() {
        assertThatThrownBy(() -> authorize(StompCommand.SEND, "/topic/aste/42"))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> authorize(StompCommand.SUBSCRIBE, "/queue/aste-user8"))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void rejectsCommandsAfterSessionRevocation() {
        when(tickets.isActive(identity)).thenReturn(false);
        assertThatThrownBy(() -> authorize(StompCommand.SEND, "/app/aste/42/join"))
                .isInstanceOf(AccessDeniedException.class);
    }

    private void authorize(StompCommand command, String destination) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
        accessor.setDestination(destination);
        accessor.setUser(identity);
        accessor.setLeaveMutable(true);
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
        interceptor.preSend(message, mock(org.springframework.messaging.MessageChannel.class));
    }
}
