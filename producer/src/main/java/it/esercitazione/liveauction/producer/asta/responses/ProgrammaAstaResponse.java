package it.esercitazione.liveauction.producer.asta.responses;

import it.esercitazione.liveauction.producer.asta.models.Asta;
import it.esercitazione.liveauction.producer.asta.models.Stato;

import java.math.BigDecimal;
import java.time.Instant;

public record ProgrammaAstaResponse(
        Long id,
        Stato stato,
        ProdottoSintesi prodotto,
        BigDecimal prezzoIniziale,
        BigDecimal incrementoMinimo,
        Instant aperturaStanzaAt,
        Instant inizioAt,
        Instant fineAt,
        Instant serverTime,
        long sequence
) {
    public static ProgrammaAstaResponse from(Asta asta, Instant serverTime) {
        return new ProgrammaAstaResponse(
                asta.getId(), asta.getStato(),
                new ProdottoSintesi(asta.getProdotto().getId(), asta.getProdotto().getNome()),
                asta.getPrezzoIniziale(), asta.getIncrementoMinimo(),
                asta.getInizioAt().minusSeconds(3 * 60), asta.getInizioAt(), asta.getFineAt(),
                serverTime, asta.getSequence());
    }

    public record ProdottoSintesi(Long id, String nome) {
    }
}
