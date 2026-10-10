package it.esercitazione.liveauction.consumer.auction;

import java.time.Instant;
import java.util.List;

public record AuctionPage(List<AuctionView> content, int page, int size,
                          long totalElements, long totalPages, Instant serverTime) {
}
