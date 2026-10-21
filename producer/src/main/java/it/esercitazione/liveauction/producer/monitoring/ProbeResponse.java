package it.esercitazione.liveauction.producer.monitoring;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Esito di una probe.")
public record ProbeResponse(@Schema(description = "`UP` quando il controllo è superato.", example = "UP") String status) {
}
