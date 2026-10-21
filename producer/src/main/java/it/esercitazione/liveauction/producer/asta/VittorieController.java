package it.esercitazione.liveauction.producer.asta;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Vittorie", description = "Aste vinte dall'utente autenticato.")
public class VittorieController {
    private final StoricoAstaService service;

    @GetMapping
    @Operation(summary = "Le mie vittorie",
            description = "Aste `CHIUSA` vinte dall'utente del token, dalla più recente. L'utente non è un parametro. "
                    + "Risposta con `Cache-Control: no-store`.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Pagina di vittorie (può essere vuota)."),
            @ApiResponse(responseCode = "400", description = "Paginazione non valida.")
    })
    public ResponseEntity<PaginaStoricoResponse<VittoriaResponse>> vittorie(
            JwtAuthenticationToken authentication,
            @Parameter(description = "Indice della pagina, parte da 0.", example = "0") @RequestParam(defaultValue = "0") @PositiveOrZero int page,
            @Parameter(description = "Elementi per pagina, da 1 a 100 (predefinito 20).", example = "20") @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(
                service.vittorie(Long.parseLong(authentication.getToken().getSubject()), page, size));
    }
}
