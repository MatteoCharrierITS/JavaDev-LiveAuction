package it.esercitazione.liveauction.producer.websocket;

import org.springframework.http.server.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;

import java.security.Principal;
import java.util.Map;

@Component
public class TicketHandshakeHandler extends DefaultHandshakeHandler {
    @Override
    protected Principal determineUser(ServerHttpRequest request, WebSocketHandler wsHandler,
                                      Map<String, Object> attributes) {
        Object identity = attributes.get(TicketHandshakeInterceptor.IDENTITY_ATTRIBUTE);
        return identity instanceof WebSocketIdentity user ? user : null;
    }
}
