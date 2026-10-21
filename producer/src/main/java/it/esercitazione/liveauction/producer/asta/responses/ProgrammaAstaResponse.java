package it.esercitazione.liveauction.producer.asta.responses;

import io.swagger.v3.oas.annotations.media.Schema;
import it.esercitazione.liveauction.producer.asta.models.Asta;
import it.esercitazione.liveauction.producer.asta.models.Stato;

import java.math.BigDecimal;
import java.time.Instant;

@Schema(description = "Asta appena programmata; gli orari sono in UTC.")

public record ProgrammaAstaResponse(
        @Schema(description = "ID dell'asta.", example = "42") Long id,
        @Schema(description = "Stato iniziale: `PROGRAMMATA`.", example = "PROGRAMMATA") Stato stato,
        ProdottoSintesi prodotto,
        @Schema(description = "Prezzo base (CRD).", example = "500.00") BigDecimal prezzoIniziale,
        @Schema(description = "Rilancio minimo (CRD).", example = "1.00") BigDecimal incrementoMinimo,
        @Schema(description = "Apertura della stanza: inizio meno 3 minuti.", example = "2026-10-03T16:27:00Z") Instant aperturaStanzaAt,
        @Schema(description = "Inizio dell'asta.", example = "2026-10-03T16:30:00Z") Instant inizioAt,
        @Schema(description = "Fine prevista: inizio più 7 minuti (può slittare per estensioni).", example = "2026-10-03T16:37:00Z") Instant fineAt,
        @Schema(description = "Orario del server.", example = "2026-10-02T10:30:00Z") Instant serverTime,
        @Schema(description = "Contatore degli eventi dell'asta.", example = "0") long sequence
) {
    public static ProgrammaAstaResponse from(Asta asta, Instant serverTime) {
        return new ProgrammaAstaResponse(
                asta.getId(), asta.getStato(),
                new ProdottoSintesi(asta.getProdotto().getId(), asta.getProdotto().getNome()),
                asta.getPrezzoIniziale(), asta.getIncrementoMinimo(),
                asta.getInizioAt().minusSeconds(3 * 60), asta.getInizioAt(), asta.getFineAt(),
                serverTime, asta.getSequence());
    }

    @Schema(description = "Riferimento sintetico al prodotto.")

    public record ProdottoSintesi(@Schema(description = "ID del prodotto.", example = "1") Long id, @Schema(description = "Nome del prodotto.", example = "Laptop Pro 15") String nome) {
    }
}
