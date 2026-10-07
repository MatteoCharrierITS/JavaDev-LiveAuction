package it.esercitazione.liveauction.producer.asta.responses;

import java.time.Instant;
import java.util.List;

public record PaginaAsteResponse(
        List<AstaSintesiResponse> content, int page, int size,
        long totalElements, long totalPages, Instant serverTime) {
}
