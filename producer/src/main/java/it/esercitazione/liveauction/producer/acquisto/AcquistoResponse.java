package it.esercitazione.liveauction.producer.acquisto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "Esito di un acquisto a prezzo fisso.")

public record AcquistoResponse(@Schema(description = "Coincide con `clientPurchaseId`.", example = "d14995b2-c122-4b80-91f9-d1db6d152df4") UUID id, @Schema(description = "ID del prodotto.", example = "3") long prodottoId, @Schema(description = "Unità acquistate.", example = "2") int quantita, @Schema(description = "Prezzo di catalogo al momento dell'acquisto (CRD).", example = "10.00") BigDecimal prezzoUnitario,
                               @Schema(description = "Totale addebitato (CRD).", example = "20.00") BigDecimal totale, @Schema(description = "Sempre `CRD`.", example = "CRD") String valuta, @Schema(description = "Istante dell'acquisto (UTC).", example = "2026-10-14T10:00:00Z") Instant acquistatoAt, @Schema(description = "true se è la ripetizione idempotente di un acquisto già eseguito.", example = "false") boolean duplicato) {}
