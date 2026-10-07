package it.esercitazione.liveauction.producer.websocket;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.converter.MessageConverter;
import org.springframework.messaging.converter.DefaultContentTypeResolver;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.util.List;

@Configuration
@EnableWebSocketMessageBroker
@EnableScheduling
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final TicketHandshakeInterceptor ticketHandshakeInterceptor;
    private final TicketHandshakeHandler ticketHandshakeHandler;
    private final AstaStompAuthorizationInterceptor authorizationInterceptor;
    private final ObjectMapper json;
    private TaskScheduler messageBrokerTaskScheduler;

    public WebSocketConfig(TicketHandshakeInterceptor ticketHandshakeInterceptor,
                           TicketHandshakeHandler ticketHandshakeHandler,
                           AstaStompAuthorizationInterceptor authorizationInterceptor,
                           ObjectMapper json) {
        this.ticketHandshakeInterceptor = ticketHandshakeInterceptor;
        this.ticketHandshakeHandler = ticketHandshakeHandler;
        this.authorizationInterceptor = authorizationInterceptor;
        this.json = json;
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
    public boolean configureMessageConverters(List<MessageConverter> converters) {
        var converter = new MappingJackson2MessageConverter();
        // Deserializza importo direttamente in BigDecimal; una sequenza deve essere intera.
        converter.setObjectMapper(json.copy().disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT));
        var contentType = new DefaultContentTypeResolver();
        contentType.setDefaultMimeType(org.springframework.util.MimeTypeUtils.APPLICATION_JSON);
        converter.setContentTypeResolver(contentType);
        // Precede anche il converter Jackson aggiunto dall'autoconfigurazione Boot.
        converters.addFirst(converter);
        return false;
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
