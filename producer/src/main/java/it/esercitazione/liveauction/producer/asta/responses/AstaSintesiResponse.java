package it.esercitazione.liveauction.producer.asta.responses;

import io.swagger.v3.oas.annotations.media.Schema;
import it.esercitazione.liveauction.producer.asta.models.Stato;

import java.math.BigDecimal;
import java.time.Instant;

@Schema(description = "Riga della lobby: sintesi di un'asta.")

public record AstaSintesiResponse(
        @Schema(description = "ID dell'asta.", example = "42") Long id, @Schema(description = "Stato dell'asta.", example = "APERTA") Stato stato, ProdottoSintesi prodotto,
        @Schema(description = "Prezzo base (CRD).", example = "500.00") BigDecimal prezzoIniziale, @Schema(description = "Rilancio minimo (CRD).", example = "1.00") BigDecimal incrementoMinimo, @Schema(description = "Offerta più alta valida; null senza offerte.", example = "630.00") BigDecimal offertaCorrente, @Schema(description = "Offerte persistite, incluse quelle ritirate.", example = "2") long numeroOfferte,
        @Schema(description = "Apertura della stanza (UTC).", example = "2026-10-03T16:27:00Z") Instant aperturaStanzaAt, @Schema(description = "Inizio (UTC).", example = "2026-10-03T16:30:00Z") Instant inizioAt, @Schema(description = "Fine corrente (UTC).", example = "2026-10-03T16:37:40Z") Instant fineAt,
        @Schema(description = "true solo se `APERTA` e nella finestra temporale; non considera ruolo e crediti.", example = "true") boolean offerteConsentite, @Schema(description = "Contatore degli eventi dell'asta.", example = "4") long sequence) {

    @Schema(description = "Riferimento sintetico al prodotto.")

    public record ProdottoSintesi(@Schema(description = "ID del prodotto.", example = "1") Long id, @Schema(description = "Nome del prodotto.", example = "Laptop Pro 15") String nome) {
    }
}
