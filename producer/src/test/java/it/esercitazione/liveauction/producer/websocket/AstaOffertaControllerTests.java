package it.esercitazione.liveauction.producer.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.esercitazione.liveauction.producer.asta.exceptions.OffertaException;
import it.esercitazione.liveauction.producer.asta.requests.OffertaRequest;
import it.esercitazione.liveauction.producer.asta.responses.OffertaResponse;
import it.esercitazione.liveauction.producer.asta.responses.StatoOfferteResponse;
import it.esercitazione.liveauction.producer.asta.services.OffertaService;
import it.esercitazione.liveauction.producer.auth.services.WebSocketTicketService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.messaging.support.MessageBuilder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AstaOffertaControllerTests {
    private final OffertaService service = mock(OffertaService.class);
    private final WebSocketTicketService tickets = mock(WebSocketTicketService.class);
    private final ObjectMapper json = new ObjectMapper();
    private final AstaOffertaController controller = new AstaOffertaController(service, tickets, json);
    private final WebSocketIdentity identity = new WebSocketIdentity(7, UUID.randomUUID(), 42);
    private final UUID bidId = UUID.randomUUID();

    @AfterEach
    void pulisciContesto() { SecurityContextHolder.clearContext(); }

    @Test
    void usesVerifiedIdentityAndRestoresSecurityContext() throws Exception {
        var original = SecurityContextHolder.getContext();
        when(tickets.isActive(identity)).thenReturn(true);
        when(service.piazza(eq(7L), eq(42L), any())).thenAnswer(invocation -> {
            var auth = (JwtAuthenticationToken) SecurityContextHolder.getContext().getAuthentication();
            assertThat(auth.getToken().getSubject()).isEqualTo("7");
            assertThat(auth.getAuthorities()).extracting("authority").containsExactly("ROLE_USER");
            return new OffertaResponse(1, bidId, BigDecimal.TEN, true, false, true,
                    new StatoOfferteResponse(42, 9, "APERTA", BigDecimal.TEN, 7L, "g***i", 1,
                            Instant.now(), null, 3, Instant.now()));
        });
        var result = controller.offri(42, identity, request());
        assertThat(result.type()).isEqualTo("BID_CONFIRMED");
        assertThat(result.duplicata()).isTrue();
        assertThat(result.snapshotRequired()).isTrue();
        assertThat(SecurityContextHolder.getContext()).isSameAs(original);
    }

    @Test
    void forwardsDomainErrorsPrivatelyAndRestoresContext() throws Exception {
        when(tickets.isActive(identity)).thenReturn(true);
        when(service.piazza(anyLong(), anyLong(), any(OffertaRequest.class)))
                .thenThrow(new OffertaException(HttpStatus.CONFLICT, "SEQUENCE_NON_AGGIORNATA", "Sequenza futura"));
        var original = SecurityContextHolder.getContext();
        var result = controller.offri(42, identity, request());
        assertThat(result.type()).isEqualTo("BID_REJECTED");
        assertThat(result.clientBidId()).isEqualTo(bidId);
        assertThat(result.snapshotRequired()).isTrue();
        assertThat(SecurityContextHolder.getContext()).isSameAs(original);
    }

    @Test
    void rejectsWrongAuctionRevokedSessionAndUntrustedPrincipal() throws Exception {
        assertThat(controller.offri(43, identity, request()).code())
                .isEqualTo("OPERAZIONE_NON_CONSENTITA");
        assertThat(controller.offri(42, identity, request()).code())
                .isEqualTo("OPERAZIONE_NON_CONSENTITA");
        assertThat(controller.offri(42, () -> "7", request()).code())
                .isEqualTo("OPERAZIONE_NON_CONSENTITA");
        verifyNoInteractions(service);
    }

    @Test
    void unexpectedFailureStillRestoresSecurityContext() throws Exception {
        when(tickets.isActive(identity)).thenReturn(true);
        when(service.piazza(anyLong(), anyLong(), any())).thenThrow(new IllegalStateException("Failure"));
        var original = SecurityContextHolder.getContext();
        assertThatThrownBy(() -> controller.offri(42, identity, request())).isInstanceOf(IllegalStateException.class);
        assertThat(SecurityContextHolder.getContext()).isSameAs(original);
    }

    @Test
    void invalidUuidAndJsonProduceSafeErrors() throws Exception {
        when(tickets.isActive(identity)).thenReturn(true);
        var result = controller.payloadNonValido(MessageBuilder.withPayload(
                body("10").replace(bidId.toString(), "invalid").getBytes(java.nio.charset.StandardCharsets.UTF_8)).build());
        assertThat(result.code()).isEqualTo("DATI_NON_VALIDI");
        assertThat(result.clientBidId()).isNull();
        assertThat(controller.payloadNonValido(MessageBuilder.withPayload("{").build()).code())
                .isEqualTo("DATI_NON_VALIDI");
        verifyNoInteractions(service);
    }

    private String body(String amount) {
        return "{\"type\":\"PLACE_BID\",\"clientBidId\":\"" + bidId
                + "\",\"importo\":" + amount + ",\"knownSequence\":0}";
    }

    private OffertaRequest request() throws Exception {
        return json.readValue(body("10"), OffertaRequest.class);
    }
}
