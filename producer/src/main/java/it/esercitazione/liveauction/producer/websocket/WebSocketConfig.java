package it.esercitazione.liveauction.producer.websocket;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
@EnableScheduling
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final TicketHandshakeInterceptor ticketHandshakeInterceptor;
    private final TicketHandshakeHandler ticketHandshakeHandler;
    private final AstaStompAuthorizationInterceptor authorizationInterceptor;
    private TaskScheduler messageBrokerTaskScheduler;

    public WebSocketConfig(TicketHandshakeInterceptor ticketHandshakeInterceptor,
                           TicketHandshakeHandler ticketHandshakeHandler,
                           AstaStompAuthorizationInterceptor authorizationInterceptor) {
        this.ticketHandshakeInterceptor = ticketHandshakeInterceptor;
        this.ticketHandshakeHandler = ticketHandshakeHandler;
        this.authorizationInterceptor = authorizationInterceptor;
    }

    @Autowired
    public void setMessageBrokerTaskScheduler(@Lazy TaskScheduler taskScheduler) {
        this.messageBrokerTaskScheduler = taskScheduler;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.setPreserveReceiveOrder(true);
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("http://localhost:8082")
                .addInterceptors(ticketHandshakeInterceptor)
                .setHandshakeHandler(ticketHandshakeHandler);
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(authorizationInterceptor);
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
        registry.setPreservePublishOrder(true);
        registry.enableSimpleBroker("/topic", "/queue")
                .setHeartbeatValue(new long[] {10_000, 10_000})
                .setTaskScheduler(messageBrokerTaskScheduler);
    }
}
