package it.esercitazione.liveauction.producer.auth.responses;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(description = "Ticket monouso per l'handshake WebSocket `/ws?ticket=...`.")

public record WebSocketTicketResponse(@Schema(description = "Valore casuale monouso, valido 30 secondi.", example = "valore-casuale-monouso") String ticket, @Schema(description = "Scadenza del ticket (UTC).", example = "2026-10-03T16:27:30Z") Instant expiresAt) {}
