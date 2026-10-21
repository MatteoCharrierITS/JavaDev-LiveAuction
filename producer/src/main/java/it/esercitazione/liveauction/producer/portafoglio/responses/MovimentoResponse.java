package it.esercitazione.liveauction.producer.portafoglio.responses;

import io.swagger.v3.oas.annotations.media.Schema;
import it.esercitazione.liveauction.producer.portafoglio.model.TipoMovimento;
import java.math.BigDecimal;
import java.time.Instant;

@Schema(description = "Movimento del ledger del portafoglio.")

public record MovimentoResponse(@Schema(description = "ID del movimento.", example = "42") long id, @Schema(description = "Asta collegata; null se non legato a un'asta.", example = "7") Long astaId, @Schema(description = "`CREDITO_INIZIALE`, `IMPOSTAZIONE_SALDO` (storico), `RISERVA_OFFERTA`, `RILASCIO_OFFERTA`, `PAGAMENTO_ASTA`, `INCASSO_ASTA`, `ACQUISTO_FISSO`, `INCASSO_FISSO`.", example = "RISERVA_OFFERTA") TipoMovimento tipo, @Schema(description = "Importo positivo (CRD).", example = "1250.00") BigDecimal importo,
                                @Schema(description = "Saldo totale dopo il movimento.", example = "10000.00") BigDecimal saldoTotaleDopo, @Schema(description = "Saldo riservato dopo il movimento.", example = "1250.00") BigDecimal saldoRiservatoDopo,
                                @Schema(description = "Istante del movimento (UTC).", example = "2026-10-09T07:00:00Z") Instant dataMovimento) {}
