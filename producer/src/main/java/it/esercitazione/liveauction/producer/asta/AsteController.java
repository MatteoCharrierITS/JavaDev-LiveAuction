package it.esercitazione.liveauction.producer.asta;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import it.esercitazione.liveauction.producer.asta.models.Stato;
import it.esercitazione.liveauction.producer.asta.responses.AstaSnapshotResponse;
import it.esercitazione.liveauction.producer.asta.responses.PaginaAsteResponse;
import it.esercitazione.liveauction.producer.asta.services.AstaLetturaService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/aste")
@RequiredArgsConstructor
@Tag(name = "Aste", description = "Lobby filtrabile e snapshot autorevole delle aste. Rotte pubbliche.")
public class AsteController {

    private final AstaLetturaService service;

    @GetMapping
    @Operation(summary = "Lobby delle aste",
            description = "Rotta pubblica. Pagina di aste ordinate per `inizioAt` e ID, con filtri combinabili. Le aste restano visibili "
                    + "anche se il prodotto viene disattivato. Filtri senza risultati o pagina oltre la fine restituiscono `content: []`. "
                    + "Risposta con `Cache-Control: no-store`.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Pagina di aste con `serverTime`."),
            @ApiResponse(responseCode = "400", description = "Parametri non validi: `stato` sconosciuto, `page` negativo o `size` fuori da 1-100 (`PARAMETRI_NON_VALIDI`).")
    })
    public ResponseEntity<PaginaAsteResponse> lobby(
            @Parameter(description = "Stato dell'asta; omesso = tutti.", example = "APERTA") @RequestParam(required = false) Stato stato,
            @Parameter(description = "Slug esatto della categoria del prodotto.", example = "informatica") @RequestParam(required = false) String categoria,
            @Parameter(description = "Testo cercato in nome, SKU e descrizione del prodotto, senza distinzione tra maiuscole e minuscole.", example = "laptop") @RequestParam(required = false) String query,
            @Parameter(description = "Indice della pagina, parte da 0.", example = "0") @RequestParam(defaultValue = "0") @PositiveOrZero int page,
            @Parameter(description = "Elementi per pagina, da 1 a 100 (predefinito 12).", example = "12") @RequestParam(defaultValue = "12") @Min(1) @Max(100) int size) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(service.cerca(stato, categoria, query, page, size));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Snapshot di un'asta",
            description = "Rotta pubblica. Stato autorevole dell'asta: prezzo corrente, leader (nome mascherato), timer e `sequence`. "
                    + "Dopo la chiusura include anche `vincitore`, `prezzoFinale` e `chiusaAt`. Risposta con `Cache-Control: no-store`.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Snapshot dell'asta."),
            @ApiResponse(responseCode = "400", description = "ID non numerico."),
            @ApiResponse(responseCode = "404", description = "`RISORSA_NON_TROVATA`: asta inesistente.")
    })
    public ResponseEntity<AstaSnapshotResponse> snapshot(@Parameter(description = "ID dell'asta.", example = "42") @PathVariable long id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.snapshot(id));
    }
}
