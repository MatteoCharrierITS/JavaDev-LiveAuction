package it.esercitazione.liveauction.producer.asta.responses;

import io.swagger.v3.oas.annotations.media.Schema;
import it.esercitazione.liveauction.producer.asta.models.Stato;
import java.time.Instant;

@Schema(description = "Esito dell'annullamento di un'asta.")

public record AnnullamentoAstaResponse(@Schema(description = "ID dell'asta.", example = "18") long id, @Schema(description = "Sempre `ANNULLATA`.", example = "ANNULLATA") Stato stato, @Schema(description = "Istante dell'annullamento (UTC); null per righe annullate prima della V12.", example = "2026-10-12T12:00:00Z") Instant annullataAt, @Schema(description = "Contatore degli eventi.", example = "3") long sequence) {}
