package it.esercitazione.liveauction.producer.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import it.esercitazione.liveauction.producer.asta.exceptions.OffertaException;
import it.esercitazione.liveauction.producer.asta.requests.OffertaRequest;
import it.esercitazione.liveauction.producer.asta.responses.OffertaResponse;
import it.esercitazione.liveauction.producer.asta.events.EventoOfferte;
import it.esercitazione.liveauction.producer.asta.services.OffertaService;
import it.esercitazione.liveauction.producer.auth.services.WebSocketTicketService;
import jakarta.validation.ConstraintViolationException;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.converter.MessageConversionException;
import org.springframework.messaging.Message;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.List;
import java.util.UUID;

@Controller
@RequiredArgsConstructor
public class AstaOffertaController {
    private final OffertaService offerte;
    private final WebSocketTicketService tickets;
    private final ObjectMapper json;

    @MessageMapping("/aste/{id}/offerte")
    @SendToUser(value = "/queue/aste", broadcast = false)
    public EsitoOfferta offri(@DestinationVariable long id, Principal principal, @Payload OffertaRequest request) {
        UUID clientBidId = request == null ? null : request.clientBidId();
        if (!(principal instanceof WebSocketIdentity identity) || identity.auctionId() != id
                || !tickets.isActive(identity)) {
            return rifiuto(clientBidId, "OPERAZIONE_NON_CONSENTITA", "Sessione non autorizzata", false);
        }
        SecurityContext precedente = SecurityContextHolder.getContext();
        try {
            // Adattatore interno per il contratto @PreAuthorize del servizio, non un JWT ricevuto dal browser.
            Jwt jwt = Jwt.withTokenValue("ws-ticket-identity").header("alg", "internal")
                    .subject(identity.getName()).claim("sid", identity.authSessionId().toString()).build();
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(new JwtAuthenticationToken(jwt,
                    List.of(new SimpleGrantedAuthority("ROLE_USER"))));
            SecurityContextHolder.setContext(context);
            OffertaResponse result = offerte.piazza(identity.userId(), id, request);
            return new EsitoOfferta("BID_CONFIRMED", result.clientBidId(), null, null,
                    result.snapshotRequired(), result.duplicata(), result.ritirata(),
                    AstaEventRelay.statoPubblico(new EventoOfferte("AUCTION_SNAPSHOT",
                            result.stato(), null, null, 0)));
        } catch (OffertaException exception) {
            String code = (String) exception.getBody().getProperties().get("code");
            return rifiuto(clientBidId, code, exception.getBody().getDetail(),
                    "SEQUENCE_NON_AGGIORNATA".equals(code));
        } catch (IllegalArgumentException | ConstraintViolationException exception) {
            return rifiuto(clientBidId, "DATI_NON_VALIDI", "Comando PLACE_BID non valido", false);
        } catch (AccessDeniedException exception) {
            return rifiuto(clientBidId, "OPERAZIONE_NON_CONSENTITA", "Offerta non autorizzata", false);
        } finally {
            // I thread STOMP vengono riutilizzati: non lasciare l'identità del comando nel thread.
            SecurityContextHolder.setContext(precedente);
        }
    }

    @MessageExceptionHandler(MessageConversionException.class)
    @SendToUser(value = "/queue/aste", broadcast = false)
    public EsitoOfferta payloadNonValido(Message<?> message) {
        UUID id = null;
        try {
            JsonNode body = message.getPayload() instanceof byte[] bytes
                    ? json.readTree(bytes) : json.readTree(message.getPayload().toString());
            id = clientBidId(body);
        } catch (java.io.IOException | IllegalArgumentException exception) {
            // Non includere il payload o i dettagli Jackson nel messaggio di errore.
        }
        return rifiuto(id, "DATI_NON_VALIDI", "JSON del comando non valido", false);
    }

    private static UUID clientBidId(JsonNode body) {
        try {
            return UUID.fromString(body.path("clientBidId").asText());
        } catch (IllegalArgumentException | NullPointerException exception) {
            return null;
        }
    }

    private static EsitoOfferta rifiuto(UUID id, String code, String message, boolean snapshotRequired) {
        return new EsitoOfferta("BID_REJECTED", id, code, message, snapshotRequired, false, false, null);
    }

    public record EsitoOfferta(String type, UUID clientBidId, String code, String message,
                               boolean snapshotRequired, boolean duplicata, boolean ritirata,
                               AstaEventRelay.StatoPubblico stato) {}
}
