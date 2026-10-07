package it.esercitazione.liveauction.producer.asta;

import it.esercitazione.liveauction.producer.asta.requests.ProgrammaAstaRequest;
import it.esercitazione.liveauction.producer.asta.responses.ProgrammaAstaResponse;
import it.esercitazione.liveauction.producer.asta.services.AstaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping("/api/v1/admin/aste")
@RequiredArgsConstructor
public class AdminAsteController {

    private final AstaService astaService;

    @PostMapping
    public ResponseEntity<ProgrammaAstaResponse> programma(
            @Valid @RequestBody ProgrammaAstaRequest request, JwtAuthenticationToken authentication) {
        long adminId = Long.parseLong(authentication.getToken().getSubject());
        ProgrammaAstaResponse response = astaService.programmaAsta(adminId, request);
        return ResponseEntity.created(URI.create("/api/v1/aste/" + response.id())).body(response);
    }
}
