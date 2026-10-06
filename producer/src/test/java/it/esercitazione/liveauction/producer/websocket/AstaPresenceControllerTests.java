package it.esercitazione.liveauction.producer.websocket;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AstaPresenceControllerTests {
    @Test
    void joinMasksUsernameAndPublishesPresenceWithoutSequence() {
        PresenceRegistry presence = new PresenceRegistry();
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        SimpMessagingTemplate messaging = mock(SimpMessagingTemplate.class);
        when(jdbc.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<String>>any(), eq(7L)))
                .thenReturn(List.of("gianni"));
        AstaPresenceController controller = new AstaPresenceController(presence, jdbc, messaging);
        SimpMessageHeaderAccessor headers = SimpMessageHeaderAccessor.create();
        headers.setSessionId("session-1");

        PresenceRegistry.PresenceSnapshot snapshot = controller.join(42,
                new WebSocketIdentity(7, UUID.randomUUID(), 42), headers);

        assertThat(snapshot.type()).isEqualTo("PRESENCE_SNAPSHOT");
        assertThat(snapshot.participants()).containsExactly("g***i");
        assertThat(snapshot.participantCount()).isEqualTo(1);
        assertThat(controller.join(42, new WebSocketIdentity(7, UUID.randomUUID(), 42), headers)
                .participantCount()).isEqualTo(1);
        verify(messaging).convertAndSend(eq("/topic/aste/42"),
                any(PresenceRegistry.PresenceChange.class));
    }
}
