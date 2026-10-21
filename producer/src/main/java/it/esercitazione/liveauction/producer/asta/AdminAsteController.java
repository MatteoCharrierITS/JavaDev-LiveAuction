package it.esercitazione.liveauction.producer.asta;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import it.esercitazione.liveauction.producer.asta.models.Stato;
import it.esercitazione.liveauction.producer.asta.requests.ProgrammaAstaRequest;
import it.esercitazione.liveauction.producer.asta.responses.AnnullamentoAstaResponse;
import it.esercitazione.liveauction.producer.asta.responses.PaginaStoricoResponse;
import it.esercitazione.liveauction.producer.asta.responses.ProgrammaAstaResponse;
import it.esercitazione.liveauction.producer.asta.responses.StoricoAstaResponse;
import it.esercitazione.liveauction.producer.asta.services.AnnullamentoAstaService;
import it.esercitazione.liveauction.producer.asta.services.AstaService;
import it.esercitazione.liveauction.producer.asta.services.StoricoAstaService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.Instant;

@RestController
@RequestMapping("/api/v1/admin/aste")
@RequiredArgsConstructor
@Tag(name = "Aste ADMIN", description = "Programmazione, annullamento e storico delle aste. Richiede il ruolo ADMIN.")
public class AdminAsteController {

    private final AstaService astaService;
    private final AnnullamentoAstaService annullamento;
    private final StoricoAstaService storico;

    @PostMapping("/{id}/annullamento")
    @Operation(summary = "Annulla un'asta",
            description = "Ammesso per aste `PROGRAMMATA`, `STANZA_APERTA` o `APERTA` prima di `fineAt` e solo se non è arrivata alcuna offerta "
                    + "(nemmeno ritirata). Eccezione: un'asta `APERTA` con offerte che risulta ancora aperta 60 secondi dopo `fineAt` (chiusura automatica bloccata) si può annullare, e la riserva del leader torna disponibile. Restituisce l'unità di stock allo stato disponibile. Ripetere il comando su un'asta già annullata "
                    + "restituisce lo stesso risultato senza altri effetti. Nessun body richiesto.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Asta annullata (o già annullata).",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = AnnullamentoAstaResponse.class))),
            @ApiResponse(responseCode = "400", description = "ID non positivo o non numerico."),
            @ApiResponse(responseCode = "404", description = "`RISORSA_NON_TROVATA`: asta inesistente."),
            @ApiResponse(responseCode = "409", description = "`ASTA_NON_ANNULLABILE` (asta chiusa o tempo oltre `fineAt`) oppure `ASTA_CON_OFFERTE`; `CHIUSURA_IN_CORSO` se l'asta con offerte è scaduta da meno di 60 secondi.")
    })
    public ResponseEntity<AnnullamentoAstaResponse> annulla(@Parameter(description = "ID dell'asta.", example = "42") @PathVariable @Positive long id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(annullamento.annulla(id));
    }

    @GetMapping("/storico")
    @Operation(summary = "Storico globale delle aste",
            description = "Aste terminali (`CHIUSA` e `ANNULLATA`) di tutti gli amministratori, anche senza vincitore, "
                    + "ordinate per data di conclusione decrescente. I filtri si combinano in AND. Risposta con `Cache-Control: no-store`.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Pagina di storico (può essere vuota)."),
            @ApiResponse(responseCode = "400", description = "Parametri non validi: `stato` non terminale, ID non positivo, date senza offset, `da` > `a`, paginazione fuori limite.")
    })
    public ResponseEntity<PaginaStoricoResponse<StoricoAstaResponse>> storico(
            @Parameter(description = "Solo aste di questo prodotto.", example = "1") @RequestParam(required = false) @Positive Long prodottoId,
            @Parameter(description = "Solo aste vinte da questo utente.", example = "9") @RequestParam(required = false) @Positive Long vincitoreId,
            @Parameter(description = "Solo `CHIUSA` o `ANNULLATA`; omesso = entrambi.", example = "CHIUSA", schema = @Schema(allowableValues = {"CHIUSA", "ANNULLATA"})) @RequestParam(required = false) Stato stato,
            @Parameter(description = "Estremo inferiore inclusivo di `conclusaAt`, ISO 8601 con offset.", example = "2026-10-12T00:00:00Z") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant da,
            @Parameter(description = "Estremo superiore inclusivo di `conclusaAt`, ISO 8601 con offset.", example = "2026-10-12T23:59:59Z") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant a,
            @Parameter(description = "Indice della pagina, parte da 0.", example = "0") @RequestParam(defaultValue = "0") @PositiveOrZero int page,
            @Parameter(description = "Elementi per pagina, da 1 a 100 (predefinito 20).", example = "20") @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(storico.storico(prodottoId, vincitoreId, stato, da, a, page, size));
    }

    @PostMapping
    @Operation(summary = "Programma un'asta",
            description = "Blocca una unità di stock del prodotto e crea l'asta in stato `PROGRAMMATA`. `inizioLocale` è interpretato nel fuso "
                    + "`Europe/Rome` e convertito in UTC; la stanza si apre 3 minuti prima dell'inizio e l'asta dura 7 minuti. "
                    + "L'amministratore creatore è quello del token. Risponde `201` con header `Location: /api/v1/aste/{id}`. "
                    + "Usa una data futura: una data passata è rifiutata con `422`.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Asta programmata.",
                    headers = @io.swagger.v3.oas.annotations.headers.Header(name = "Location", description = "URL dell'asta creata", schema = @Schema(type = "string", example = "/api/v1/aste/42")),
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ProgrammaAstaResponse.class))),
            @ApiResponse(responseCode = "400", description = "JSON malformato o `prodottoId` mancante o non positivo."),
            @ApiResponse(responseCode = "404", description = "`RISORSA_NON_TROVATA`: prodotto inesistente."),
            @ApiResponse(responseCode = "409", description = "`PRODOTTO_NON_DISPONIBILE`: nessuna unità disponibile."),
            @ApiResponse(responseCode = "422", description = "`PRODOTTO_NON_ASTABILE`, `DATA_INIZIO_NON_VALIDA` (data o fuso mancanti o non validi) oppure `PREZZO_INIZIALE_NON_VALIDO`.")
    })
    public ResponseEntity<ProgrammaAstaResponse> programma(
            @Valid @RequestBody ProgrammaAstaRequest request, JwtAuthenticationToken authentication) {
        long adminId = Long.parseLong(authentication.getToken().getSubject());
        ProgrammaAstaResponse response = astaService.programmaAsta(adminId, request);
        return ResponseEntity.created(URI.create("/api/v1/aste/" + response.id())).body(response);
    }
}
