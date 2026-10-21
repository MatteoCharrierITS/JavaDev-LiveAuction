package it.esercitazione.liveauction.producer.websocket;

import it.esercitazione.liveauction.producer.asta.AccessoStanzaService;
import it.esercitazione.liveauction.producer.auth.models.Ruolo;
import it.esercitazione.liveauction.producer.auth.services.WebSocketTicketService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.access.AccessDeniedException;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AstaStompAuthorizationInterceptorTests {
    private final WebSocketTicketService tickets = mock(WebSocketTicketService.class);
    private final AccessoStanzaService accessoStanza = mock(AccessoStanzaService.class);
    private final SimpMessagingTemplate template = mock(SimpMessagingTemplate.class);
    @SuppressWarnings("unchecked")
    private final ObjectProvider<SimpMessagingTemplate> messaging = mock(ObjectProvider.class);
    private final AstaStompAuthorizationInterceptor interceptor =
            new AstaStompAuthorizationInterceptor(tickets, accessoStanza, messaging, new ObjectMapper());
    private final WebSocketIdentity identity = new WebSocketIdentity(7, UUID.randomUUID(), 42);
    private final WebSocketIdentity admin = new WebSocketIdentity(1, UUID.randomUUID(), 42, Ruolo.ADMIN);

    @BeforeEach
    void activeSession() {
        when(tickets.isActive(identity)).thenReturn(true);
        when(tickets.isActive(admin)).thenReturn(true);
        when(messaging.getObject()).thenReturn(template);
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

    @Test
    void userMayPlaceBidsOnItsAuction() {
        assertThatCode(() -> authorize(StompCommand.SEND, "/app/aste/42/offerte"))
                .doesNotThrowAnyException();
    }

    @Test
    void adminObservesTheRoomButCannotPlaceBids() {
        assertThatCode(() -> authorize(admin, StompCommand.CONNECT, null)).doesNotThrowAnyException();
        assertThatCode(() -> authorize(admin, StompCommand.SUBSCRIBE, "/topic/aste/42"))
                .doesNotThrowAnyException();
        assertThatCode(() -> authorize(admin, StompCommand.SUBSCRIBE, "/user/queue/aste"))
                .doesNotThrowAnyException();
        assertThatCode(() -> authorize(admin, StompCommand.SEND, "/app/aste/42/join"))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> authorize(admin, StompCommand.SUBSCRIBE, "/topic/aste/43"))
                .isInstanceOf(AccessDeniedException.class);
        verify(template, never()).convertAndSendToUser(any(), any(), any(), any(MessageHeaders.class));
    }

    @Test
    void adminBidIsStoppedAndRejectedOnlyToTheSendingSession() {
        UUID bidId = UUID.randomUUID();
        Message<?> result = authorize(admin, StompCommand.SEND, "/app/aste/42/offerte",
                ("{\"type\":\"PLACE_BID\",\"clientBidId\":\"" + bidId + "\",\"importo\":50,\"knownSequence\":1}")
                        .getBytes(StandardCharsets.UTF_8));
        assertThat(result).as("il comando non raggiunge il controller").isNull();
        var headers = org.mockito.ArgumentCaptor.forClass(MessageHeaders.class);
        var payload = org.mockito.ArgumentCaptor.forClass(Object.class);
        verify(template).convertAndSendToUser(eq("1"), eq("/queue/aste"), payload.capture(), headers.capture());
        assertThat(SimpMessageHeaderAccessor.getSessionId(headers.getValue())).isEqualTo("session-1");
        assertThat(payload.getValue()).isEqualTo(new AstaOffertaController.EsitoOfferta("BID_REJECTED", bidId,
                "OPERAZIONE_NON_CONSENTITA", "Un amministratore osserva la stanza senza offrire",
                false, false, false, null));
    }

    @Test
    void rejectsAdminWhoseSessionOrRoleIsNoLongerValid() {
        when(tickets.isActive(admin)).thenReturn(false);
        assertThatThrownBy(() -> authorize(admin, StompCommand.SUBSCRIBE, "/topic/aste/42"))
                .isInstanceOf(AccessDeniedException.class);
    }

    private void authorize(StompCommand command, String destination) {
        authorize(identity, command, destination);
    }

    private Message<?> authorize(WebSocketIdentity user, StompCommand command, String destination) {
        return authorize(user, command, destination, new byte[0]);
    }

    private Message<?> authorize(WebSocketIdentity user, StompCommand command, String destination, byte[] body) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
        accessor.setSessionId("session-1");
        if (destination != null) {
            accessor.setDestination(destination);
        }
        accessor.setUser(user);
        accessor.setLeaveMutable(true);
        Message<byte[]> message = MessageBuilder.createMessage(body, accessor.getMessageHeaders());
        return interceptor.preSend(message, mock(org.springframework.messaging.MessageChannel.class));
    }
}
