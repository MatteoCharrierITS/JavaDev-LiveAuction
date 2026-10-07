package it.esercitazione.liveauction.producer.asta.responses;

import java.math.BigDecimal;
import java.time.Instant;

/** Stato restituito dai comandi; non sostituisce il contratto dello snapshot REST completo. */
public record StatoOfferteResponse(
        long auctionId, long prodottoId, String stato,
        BigDecimal offertaCorrente, Long migliorOfferenteId, String offerenteDisplay,
        long numeroOfferte, Instant fineAt, Long vincitoreId,
        long sequence, Instant serverTime
) {}
