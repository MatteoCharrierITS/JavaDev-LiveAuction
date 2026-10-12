package it.esercitazione.liveauction.producer.asta.responses;

import it.esercitazione.liveauction.producer.asta.models.Stato;
import java.time.Instant;

public record AnnullamentoAstaResponse(long id, Stato stato, Instant annullataAt, long sequence) {}
