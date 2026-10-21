package it.esercitazione.liveauction.producer.acquisto;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;

@RestController @RequiredArgsConstructor
@Tag(name = "Acquisti", description = "Acquisto a prezzo fisso, idempotente tramite UUID.")
public class AcquistoController {
    private final AcquistoService service;
    @PostMapping("/api/v1/prodotti/{id}/acquisti")
    @Operation(summary = "Acquista un prodotto a prezzo fisso",
            description = "Addebita il compratore al prezzo di catalogo, accredita l'amministratore negoziante, scrive i movimenti e "
                    + "aggiunge il prodotto all'inventario. Idempotente: ripetere lo stesso `clientPurchaseId` con gli stessi dati "
                    + "restituisce `200` con `duplicato: true` e nessun nuovo effetto. Per un nuovo acquisto genera un nuovo UUID. "
                    + "Risposta con `Cache-Control: no-store`.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Acquisto eseguito.",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = AcquistoResponse.class))),
            @ApiResponse(responseCode = "200", description = "Ripetizione idempotente: risultato originario con `duplicato: true`.",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = AcquistoResponse.class))),
            @ApiResponse(responseCode = "400", description = "Body, UUID o quantità (intera, 1-100) non validi."),
            @ApiResponse(responseCode = "404", description = "`RISORSA_NON_TROVATA`: prodotto assente o non pubblico."),
            @ApiResponse(responseCode = "409", description = "`PRODOTTO_NON_ACQUISTABILE` (senza prezzo fisso), `PRODOTTO_NON_DISPONIBILE`, `SALDO_INSUFFICIENTE`, `IMPORTO_ACQUISTO_ECCESSIVO`, `LIMITE_SALDO_SUPERATO` o `CLIENT_PURCHASE_ID_GIA_UTILIZZATO` (UUID già usato con dati diversi)."),
            @ApiResponse(responseCode = "503", description = "`ACQUISTI_NON_CONFIGURATI`: conto ADMIN negoziante non configurato.")
    })
    public ResponseEntity<AcquistoResponse> compra(@Parameter(description = "ID del prodotto.", example = "1") @PathVariable long id,
            @Valid @RequestBody AcquistoRequest request, JwtAuthenticationToken auth) {
        var result=service.compra(Long.parseLong(auth.getToken().getSubject()),id,request);
        return ResponseEntity.status(result.duplicato()?200:201).cacheControl(CacheControl.noStore()).body(result);
    }
}
