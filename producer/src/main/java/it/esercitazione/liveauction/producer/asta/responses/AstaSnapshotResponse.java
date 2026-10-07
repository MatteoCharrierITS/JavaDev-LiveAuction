package it.esercitazione.liveauction.producer.asta.responses;

import com.fasterxml.jackson.annotation.JsonInclude;
import it.esercitazione.liveauction.producer.asta.models.Stato;
import it.esercitazione.liveauction.producer.asta.responses.AstaSintesiResponse.ProdottoSintesi;

import java.math.BigDecimal;
import java.time.Instant;

public record AstaSnapshotResponse(
        Long id, Stato stato, ProdottoSintesi prodotto,
        BigDecimal prezzoIniziale, BigDecimal incrementoMinimo, BigDecimal offertaCorrente,
        Partecipante migliorOfferente, long numeroOfferte,
        Instant aperturaStanzaAt, Instant inizioAt, Instant fineAt, Instant serverTime,
        boolean offerteConsentite, long sequence,
        @JsonInclude(JsonInclude.Include.NON_NULL) Partecipante vincitore,
        @JsonInclude(JsonInclude.Include.NON_NULL) BigDecimal prezzoFinale,
        @JsonInclude(JsonInclude.Include.NON_NULL) Instant chiusaAt) {

    public record Partecipante(Long id, String displayName) {
    }
}
