package it.esercitazione.liveauction.producer.asta.responses;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "Pagina di storico o vittorie.")

public record PaginaStoricoResponse<T>(@Schema(description = "Elementi della pagina.") List<T> content, @Schema(description = "Indice della pagina (da 0).", example = "0") int page, @Schema(description = "Dimensione richiesta.", example = "20") int size,
                                       @Schema(description = "Elementi totali.", example = "1") long totalElements, @Schema(description = "Pagine totali (0 se vuoto).", example = "1") long totalPages) {}
