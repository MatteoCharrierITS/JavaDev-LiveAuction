package it.esercitazione.liveauction.producer.portafoglio;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import it.esercitazione.liveauction.producer.portafoglio.responses.PortafoglioResponse;
import it.esercitazione.liveauction.producer.portafoglio.services.PortafoglioService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/me/portafoglio")
@RequiredArgsConstructor
@Tag(name = "Portafoglio", description = "Saldo in crediti e movimenti dell'utente, in sola lettura.")
public class PortafoglioController {
    private final PortafoglioService service;

    @GetMapping
    @Operation(summary = "Il mio portafoglio",
            description = "Saldo totale, riservato e disponibile (CRD) con i movimenti del ledger, dal più recente. Non esistono API di ricarica. "
                    + "Saldi e movimenti provengono dalla stessa fotografia transazionale.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Saldo e pagina di movimenti."),
            @ApiResponse(responseCode = "400", description = "`PAGINAZIONE_NON_VALIDA`: `page` < 0 o `size` fuori da 1-100."),
            @ApiResponse(responseCode = "404", description = "`RISORSA_NON_TROVATA`: portafoglio mancante.")
    })
    public PortafoglioResponse leggi(JwtAuthenticationToken authentication,
            @Parameter(description = "Indice della pagina, parte da 0.", schema = @Schema(type = "integer", defaultValue = "0", minimum = "0"), example = "0") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Elementi per pagina, da 1 a 100 (predefinito 20).", schema = @Schema(type = "integer", defaultValue = "20", minimum = "1", maximum = "100"), example = "20") @RequestParam(defaultValue = "20") int size) {
        return service.leggi(Long.parseLong(authentication.getToken().getSubject()), page, size);
    }

}
