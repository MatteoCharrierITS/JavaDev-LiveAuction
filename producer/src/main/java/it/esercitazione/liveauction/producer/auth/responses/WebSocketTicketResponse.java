package it.esercitazione.liveauction.producer.auth.responses;

import java.time.Instant;

public record WebSocketTicketResponse(String ticket, Instant expiresAt) {}
