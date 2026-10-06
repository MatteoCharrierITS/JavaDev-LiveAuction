package it.esercitazione.liveauction.producer.websocket;

import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

@Component
@RequiredArgsConstructor
public class PresenceDisconnectListener {
    private final PresenceRegistry presence;
    private final SimpMessagingTemplate messaging;

    @EventListener
    public void onDisconnect(SessionDisconnectEvent event) {
        presence.leave(event.getSessionId()).ifPresent(change ->
                messaging.convertAndSend("/topic/aste/" + change.auctionId(), change));
    }
}
