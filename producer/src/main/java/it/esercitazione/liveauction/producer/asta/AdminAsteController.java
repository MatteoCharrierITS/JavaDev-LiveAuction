package it.esercitazione.liveauction.producer.asta;

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
public class AdminAsteController {

    private final AstaService astaService;
    private final AnnullamentoAstaService annullamento;
    private final StoricoAstaService storico;

    @PostMapping("/{id}/annullamento")
    public ResponseEntity<AnnullamentoAstaResponse> annulla(@PathVariable @Positive long id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(annullamento.annulla(id));
    }

    @GetMapping("/storico")
    public ResponseEntity<PaginaStoricoResponse<StoricoAstaResponse>> storico(
            @RequestParam(required = false) @Positive Long prodottoId,
            @RequestParam(required = false) @Positive Long vincitoreId,
            @RequestParam(required = false) Stato stato,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant da,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant a,
            @RequestParam(defaultValue = "0") @PositiveOrZero int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(storico.storico(prodottoId, vincitoreId, stato, da, a, page, size));
    }

    @PostMapping
    public ResponseEntity<ProgrammaAstaResponse> programma(
            @Valid @RequestBody ProgrammaAstaRequest request, JwtAuthenticationToken authentication) {
        long adminId = Long.parseLong(authentication.getToken().getSubject());
        ProgrammaAstaResponse response = astaService.programmaAsta(adminId, request);
        return ResponseEntity.created(URI.create("/api/v1/aste/" + response.id())).body(response);
    }
}
