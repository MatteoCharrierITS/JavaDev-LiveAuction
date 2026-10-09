package it.esercitazione.liveauction.producer.portafoglio.responses;

import java.math.BigDecimal;
import java.util.List;

public record PortafoglioResponse(BigDecimal saldoTotale, BigDecimal saldoRiservato,
        BigDecimal saldoDisponibile, String valuta, List<MovimentoResponse> movimenti,
        int page, int size, long totalElements, long totalPages) {}
