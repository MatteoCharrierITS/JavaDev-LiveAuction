package it.esercitazione.liveauction.producer.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.esercitazione.liveauction.producer.asta.events.AstaTransizioneEvent;
import it.esercitazione.liveauction.producer.asta.events.EventoOfferte;
import it.esercitazione.liveauction.producer.asta.models.Stato;
import it.esercitazione.liveauction.producer.asta.responses.StatoOfferteResponse;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.converter.MessageConverter;
import org.springframework.messaging.support.MessageBuilder;
import it.esercitazione.liveauction.producer.asta.requests.OffertaRequest;
import java.util.ArrayList;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class AstaEventRelayTests {
    @Test
    void jsonConverterPreservesAmountPrecisionAndRejectsFractionalSequence() {
        var config = new WebSocketConfig(mock(TicketHandshakeInterceptor.class), mock(TicketHandshakeHandler.class),
                mock(AstaStompAuthorizationInterceptor.class), new ObjectMapper());
        var converters = new ArrayList<MessageConverter>();
        // Simula il converter permissivo registrato da Boot prima del nostro.
        converters.add(new org.springframework.messaging.converter.MappingJackson2MessageConverter());
        config.configureMessageConverters(converters);
        String body = "{\"type\":\"PLACE_BID\",\"clientBidId\":\"" + UUID.randomUUID()
                + "\",\"importo\":11.000000000000000000001,\"knownSequence\":1}";
        var message = MessageBuilder.withPayload(body.getBytes(java.nio.charset.StandardCharsets.UTF_8))
                .setHeader("contentType", org.springframework.util.MimeTypeUtils.APPLICATION_JSON).build();
        var request = (OffertaRequest) converters.getFirst().fromMessage(message, OffertaRequest.class);
        assertThat(request.importo().scale()).isEqualTo(21);
        var fractional = MessageBuilder.withPayload(body.replace("\"knownSequence\":1", "\"knownSequence\":1.5")
                .getBytes(java.nio.charset.StandardCharsets.UTF_8))
                .setHeader("contentType", org.springframework.util.MimeTypeUtils.APPLICATION_JSON).build();
        assertThatThrownBy(() -> converters.getFirst().fromMessage(fractional, OffertaRequest.class))
                .isInstanceOf(org.springframework.messaging.converter.MessageConversionException.class);
        assertThatThrownBy(() -> converters.getFirst().fromMessage(
                MessageBuilder.withPayload(fractional.getPayload()).build(), OffertaRequest.class))
                .isInstanceOf(org.springframework.messaging.converter.MessageConversionException.class);
    }

    @Test
    void forwardsBothLifecycleEventsWithoutChangingSequence() {
        var messaging = mock(SimpMessagingTemplate.class);
        var relay = new AstaEventRelay(messaging);
        for (var type : AstaTransizioneEvent.Tipo.values()) {
            var event = new AstaTransizioneEvent(type, 42L, 3, Instant.now(), Stato.APERTA,
                    Instant.now(), Instant.now(), Instant.now());
            relay.transizione(event);
            verify(messaging).convertAndSend("/topic/aste/42", event);
        }
    }

    @Test
    void adaptsAllBidEventsWithoutPublishingInternalUserIds() throws Exception {
        var messaging = mock(SimpMessagingTemplate.class);
        var relay = new AstaEventRelay(messaging);
        var json = new ObjectMapper().findAndRegisterModules();
        var state = new StatoOfferteResponse(42, 9, "CHIUSA", BigDecimal.TEN, 7L, "g***i", 1,
                Instant.now(), 7L, 3, Instant.now());
        for (String type : new String[]{"BID_ACCEPTED", "AUCTION_CLOSED", "AUCTION_SNAPSHOT"}) {
            var event = new EventoOfferte(type, state, UUID.randomUUID(), BigDecimal.TEN, 20);
            var dto = AstaEventRelay.statoPubblico(event);
            relay.offerta(event);
            verify(messaging).convertAndSend("/topic/aste/42", dto);
            var payload = json.valueToTree(dto);
            assertThat(payload.get("sequence").asLong()).isEqualTo(3);
            assertThat(payload.get("vincitoreDisplay").asText()).isEqualTo("g***i");
            assertThat(payload.has("migliorOfferenteId")).isFalse();
            assertThat(payload.has("vincitoreId")).isFalse();
            assertThat(payload.has("clientBidId")).isFalse();
        }
    }
}
