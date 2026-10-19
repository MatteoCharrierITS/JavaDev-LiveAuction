package it.esercitazione.liveauction.consumer.auction;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record VictoryPage(List<Victory> content, int page, int size, long totalElements, long totalPages) {
    public record Victory(long id, Product prodotto, BigDecimal prezzoFinale, Instant chiusaAt) {}
    public record Product(long id, String nome, String immagineUrl) {}
}
