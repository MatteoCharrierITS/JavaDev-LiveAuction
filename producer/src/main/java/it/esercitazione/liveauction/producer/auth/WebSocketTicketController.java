package it.esercitazione.liveauction.producer.auth;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Parameter;
import it.esercitazione.liveauction.producer.auth.responses.WebSocketTicketResponse;
import it.esercitazione.liveauction.producer.auth.services.WebSocketTicketService;
import it.esercitazione.liveauction.producer.auth.services.WebSocketTicketStore;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/aste")
@RequiredArgsConstructor
@Tag(name = "Stanza live", description = "Ticket monouso per la stanza WebSocket/STOMP (`/ws`) di un'asta.")
public class WebSocketTicketController {
    private final WebSocketTicketService tickets;

    @PostMapping("/{id}/ticket")
    @Operation(summary = "Richiede il ticket per la stanza live",
            description = "Emette un ticket monouso, valido 30 secondi e legato a utente, sessione e asta. Va passato "
                    + "all'handshake `ws://<host>:8081/ws?ticket=<ticket>`. Si può richiedere solo da tre minuti prima "
                    + "dell'inizio. Il protocollo STOMP non è descritto da OpenAPI: vedi `docs/07-liveauction.md`.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Ticket emesso.",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = WebSocketTicketResponse.class))),
            @ApiResponse(responseCode = "404", description = "`RISORSA_NON_TROVATA`: asta inesistente."),
            @ApiResponse(responseCode = "409", description = "`STANZA_NON_APERTA`: mancano più di tre minuti all'inizio, oppure l'asta è annullata.")
    })
    public ResponseEntity<WebSocketTicketResponse> issue(
            @Parameter(description = "ID dell'asta", example = "42") @PathVariable long id,
                                                          JwtAuthenticationToken authentication) {
        WebSocketTicketStore.IssuedTicket issued = tickets.issue(id, authentication);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(new WebSocketTicketResponse(issued.ticket(), issued.expiresAt()));
    }
}
