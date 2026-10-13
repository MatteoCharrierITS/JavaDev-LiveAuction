package it.esercitazione.liveauction.consumer.admin;

import java.time.Instant;

public record CancellationResult(long id, String stato, Instant annullataAt, long sequence) {}
