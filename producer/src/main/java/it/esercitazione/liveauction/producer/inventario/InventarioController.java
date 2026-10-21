package it.esercitazione.liveauction.producer.inventario;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import it.esercitazione.liveauction.producer.inventario.responses.PaginaInventarioResponse;
import it.esercitazione.liveauction.producer.inventario.services.InventarioService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/me/inventario")
@RequiredArgsConstructor
@Tag(name = "Inventario", description = "Prodotti posseduti dall'utente autenticato.")
public class InventarioController {
    private final InventarioService service;

    @GetMapping
    @Operation(summary = "Il mio inventario",
            description = "Una riga per prodotto posseduto (quantità positiva), ordinata per ID prodotto. L'utente è quello del token. "
                    + "Risposta con `Cache-Control: no-store`.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Pagina di inventario (può essere vuota)."),
            @ApiResponse(responseCode = "400", description = "`PAGINAZIONE_NON_VALIDA`: `page` < 0 o `size` fuori da 1-100; parametri non numerici.")
    })
    public ResponseEntity<PaginaInventarioResponse> personale(JwtAuthenticationToken auth,
            @Parameter(description = "Indice della pagina, parte da 0.", schema = @Schema(type = "integer", defaultValue = "0", minimum = "0"), example = "0") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Elementi per pagina, da 1 a 100 (predefinito 20).", schema = @Schema(type = "integer", defaultValue = "20", minimum = "1", maximum = "100"), example = "20") @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(
                service.personale(Long.parseLong(auth.getToken().getSubject()), page, size));
    }
}
