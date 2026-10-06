package it.esercitazione.liveauction.producer.auth.services;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class WebSocketTicketStoreTests {
    private final WebSocketTicketStore store = new WebSocketTicketStore();
    private final Instant now = Instant.parse("2026-10-05T10:00:00Z");

    @Test
    void ticketIsBoundToItsDataAndCanOnlyBeConsumedOnce() {
        UUID sessionId = UUID.randomUUID();
        WebSocketTicketStore.IssuedTicket issued = store.issue(7, sessionId, 42, now);

        assertThat(issued.expiresAt()).isEqualTo(now.plusSeconds(30));
        assertThat(store.consume(issued.ticket(), now.plusSeconds(1)))
                .contains(new WebSocketTicketStore.TicketData(7, sessionId, 42, issued.expiresAt()));
        assertThat(store.consume(issued.ticket(), now.plusSeconds(2))).isEmpty();
    }

    @Test
    void expiredOrMalformedTicketIsRejected() {
        WebSocketTicketStore.IssuedTicket issued = store.issue(7, UUID.randomUUID(), 42, now);

        assertThat(store.consume("invalid", now)).isEmpty();
        assertThat(store.consume(issued.ticket(), now.plusSeconds(30))).isEmpty();
        assertThat(store.consume(issued.ticket(), now.plusSeconds(31))).isEmpty();
    }
}
