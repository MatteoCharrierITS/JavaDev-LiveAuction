package it.esercitazione.liveauction.producer.prodotto.responses;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "Pagina di risultati.")

public record PaginaResponse<T>(
        @Schema(description = "Elementi della pagina.") List<T> content,
        @Schema(description = "Indice della pagina (da 0).", example = "0") int page,
        @Schema(description = "Dimensione richiesta.", example = "12") int size,
        @Schema(description = "Elementi totali.", example = "1") long totalElements,
        @Schema(description = "Pagine totali.", example = "1") int totalPages
) {
}
