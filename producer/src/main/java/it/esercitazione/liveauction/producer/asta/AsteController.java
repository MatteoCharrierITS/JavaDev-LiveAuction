package it.esercitazione.liveauction.producer.asta;

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
public class AsteController {

    private final AstaLetturaService service;

    @GetMapping
    public ResponseEntity<PaginaAsteResponse> lobby(
            @RequestParam(required = false) Stato stato,
            @RequestParam(required = false) String categoria,
            @RequestParam(required = false) String query,
            @RequestParam(defaultValue = "0") @PositiveOrZero int page,
            @RequestParam(defaultValue = "12") @Min(1) @Max(100) int size) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(service.cerca(stato, categoria, query, page, size));
    }

    @GetMapping("/{id}")
    public ResponseEntity<AstaSnapshotResponse> snapshot(@PathVariable long id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.snapshot(id));
    }
}
