package it.esercitazione.liveauction.producer.asta.responses;

import io.swagger.v3.oas.annotations.media.Schema;
import it.esercitazione.liveauction.producer.asta.models.Stato;
import it.esercitazione.liveauction.producer.asta.responses.AstaSintesiResponse.ProdottoSintesi;
import java.math.BigDecimal;
import java.time.Instant;

@Schema(description = "Asta terminale (chiusa o annullata) nello storico ADMIN.")

public record StoricoAstaResponse(@Schema(description = "ID dell'asta.", example = "18") long id, @Schema(description = "`CHIUSA` o `ANNULLATA`.", example = "CHIUSA") Stato stato, ProdottoSintesi prodotto, @Schema(description = "ID del vincitore; null se annullata o senza vincitore.", example = "9") Long vincitoreId,
                                  @Schema(description = "Prezzo finale (CRD); null se annullata o senza vincitore.", example = "75.00") BigDecimal prezzoFinale, @Schema(description = "Inizio (UTC).", example = "2026-10-12T12:30:00Z") Instant inizioAt, @Schema(description = "Fine (UTC).", example = "2026-10-12T12:37:00Z") Instant fineAt,
                                  @Schema(description = "Chiusura o annullamento (UTC).", example = "2026-10-12T12:37:20Z") Instant conclusaAt) {}
