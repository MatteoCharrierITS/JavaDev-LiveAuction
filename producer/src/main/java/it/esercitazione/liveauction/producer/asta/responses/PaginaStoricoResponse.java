package it.esercitazione.liveauction.producer.asta.responses;

import java.util.List;

public record PaginaStoricoResponse<T>(List<T> content, int page, int size,
                                       long totalElements, long totalPages) {}
