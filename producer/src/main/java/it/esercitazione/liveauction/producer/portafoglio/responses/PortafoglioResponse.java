package it.esercitazione.liveauction.producer.portafoglio.responses;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.List;

@Schema(description = "Saldo e movimenti paginati dell'utente autenticato.")

public record PortafoglioResponse(@Schema(description = "Crediti posseduti.", example = "10000.00") BigDecimal saldoTotale, @Schema(description = "Crediti impegnati in offerte.", example = "1250.00") BigDecimal saldoRiservato,
        @Schema(description = "Totale meno riservato: spendibile.", example = "8750.00") BigDecimal saldoDisponibile, @Schema(description = "Sempre `CRD`.", example = "CRD") String valuta, @Schema(description = "Movimenti, dal più recente.") List<MovimentoResponse> movimenti,
        @Schema(description = "Indice della pagina (da 0).", example = "0") int page, @Schema(description = "Dimensione richiesta.", example = "20") int size, @Schema(description = "Movimenti totali.", example = "1") long totalElements, @Schema(description = "Pagine totali.", example = "1") long totalPages) {}
