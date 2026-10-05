package it.esercitazione.liveauction.producer.auth;

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
public class WebSocketTicketController {
    private final WebSocketTicketService tickets;

    @PostMapping("/{id}/ticket")
    public ResponseEntity<WebSocketTicketResponse> issue(@PathVariable long id,
                                                          JwtAuthenticationToken authentication) {
        WebSocketTicketStore.IssuedTicket issued = tickets.issue(id, authentication);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(new WebSocketTicketResponse(issued.ticket(), issued.expiresAt()));
    }
}
