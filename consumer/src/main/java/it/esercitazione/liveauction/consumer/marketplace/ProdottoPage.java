package it.esercitazione.liveauction.consumer.marketplace;

import java.util.List;

public record ProdottoPage(List<ProdottoView> content, int page, int size,
                           long totalElements, int totalPages) {}
