package it.esercitazione.liveauction.consumer.admin;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record AuctionHistoryPage(List<History> content, int page, int size, long totalElements, long totalPages) {
    public record History(long id, String stato, Product prodotto, Long vincitoreId,
            BigDecimal prezzoFinale, Instant inizioAt, Instant fineAt, Instant conclusaAt) {}
    public record Product(long id, String nome) {}
}
