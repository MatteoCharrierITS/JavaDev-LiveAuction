package it.esercitazione.liveauction.producer.asta;

import it.esercitazione.liveauction.producer.asta.responses.PaginaStoricoResponse;
import it.esercitazione.liveauction.producer.asta.responses.VittoriaResponse;
import it.esercitazione.liveauction.producer.asta.services.StoricoAstaService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/me/vittorie")
@RequiredArgsConstructor
public class VittorieController {
    private final StoricoAstaService service;

    @GetMapping
    public ResponseEntity<PaginaStoricoResponse<VittoriaResponse>> vittorie(
            JwtAuthenticationToken authentication,
            @RequestParam(defaultValue = "0") @PositiveOrZero int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(
                service.vittorie(Long.parseLong(authentication.getToken().getSubject()), page, size));
    }
}
