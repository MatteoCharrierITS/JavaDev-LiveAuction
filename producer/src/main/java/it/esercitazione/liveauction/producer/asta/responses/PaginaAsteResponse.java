package it.esercitazione.liveauction.producer.asta.responses;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

@Schema(description = "Pagina della lobby delle aste.")

public record PaginaAsteResponse(
        @Schema(description = "Aste della pagina.") List<AstaSintesiResponse> content, @Schema(description = "Indice della pagina (da 0).", example = "0") int page, @Schema(description = "Dimensione richiesta.", example = "12") int size,
        @Schema(description = "Aste totali.", example = "1") long totalElements, @Schema(description = "Pagine totali.", example = "1") long totalPages, @Schema(description = "Orario del server (UTC).", example = "2026-10-03T16:31:00Z") Instant serverTime) {
}
