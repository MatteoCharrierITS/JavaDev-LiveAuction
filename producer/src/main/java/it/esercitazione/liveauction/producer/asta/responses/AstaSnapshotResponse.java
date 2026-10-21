package it.esercitazione.liveauction.producer.asta.responses;

import io.swagger.v3.oas.annotations.media.Schema;
import com.fasterxml.jackson.annotation.JsonInclude;
import it.esercitazione.liveauction.producer.asta.models.Stato;
import it.esercitazione.liveauction.producer.asta.responses.AstaSintesiResponse.ProdottoSintesi;

import java.math.BigDecimal;
import java.time.Instant;

@Schema(description = "Snapshot autorevole di un'asta; in `CHIUSA` include anche l'esito.")

public record AstaSnapshotResponse(
        @Schema(description = "ID dell'asta.", example = "42") Long id, @Schema(description = "Stato dell'asta.", example = "APERTA") Stato stato, ProdottoSintesi prodotto,
        @Schema(description = "Prezzo base (CRD).", example = "500.00") BigDecimal prezzoIniziale, @Schema(description = "Rilancio minimo (CRD).", example = "1.00") BigDecimal incrementoMinimo, @Schema(description = "Prezzo del leader operativo; null senza offerte.", example = "630.00") BigDecimal offertaCorrente,
        @Schema(description = "Leader operativo; null senza offerte.") Partecipante migliorOfferente, @Schema(description = "Offerte persistite, incluse quelle ritirate.", example = "2") long numeroOfferte,
        @Schema(description = "Apertura della stanza (UTC).", example = "2026-10-03T16:27:00Z") Instant aperturaStanzaAt, @Schema(description = "Inizio (UTC).", example = "2026-10-03T16:30:00Z") Instant inizioAt, @Schema(description = "Fine corrente, con le estensioni (UTC).", example = "2026-10-03T16:37:00Z") Instant fineAt, @Schema(description = "Orario del server (UTC).", example = "2026-10-03T16:28:05Z") Instant serverTime,
        @Schema(description = "true solo se `APERTA` e `inizioAt` <= `serverTime` < `fineAt`.", example = "true") boolean offerteConsentite, @Schema(description = "Contatore degli eventi; confrontalo con quello degli eventi STOMP.", example = "4") long sequence,
        @JsonInclude(JsonInclude.Include.NON_NULL) @Schema(description = "Solo in `CHIUSA` con vincitore.") Partecipante vincitore,
        @JsonInclude(JsonInclude.Include.NON_NULL) @Schema(description = "Solo in `CHIUSA` con vincitore (CRD).", example = "630.00") BigDecimal prezzoFinale,
        @JsonInclude(JsonInclude.Include.NON_NULL) @Schema(description = "Solo se registrata (UTC).", example = "2026-10-03T16:38:40Z") Instant chiusaAt) {

    @Schema(description = "Utente con nome mascherato.")

    public record Partecipante(@Schema(description = "ID dell'utente.", example = "9") Long id, @Schema(description = "Username mascherato.", example = "g***i") String displayName) {
    }
}
