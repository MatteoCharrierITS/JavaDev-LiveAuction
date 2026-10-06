package it.esercitazione.liveauction.producer.websocket;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PresenceRegistryTests {
    private final PresenceRegistry presence = new PresenceRegistry();

    @Test
    void multipleTabsCountAsOneUserAndDisconnectIsIdempotent() {
        PresenceRegistry.JoinResult first = presence.join(42, 7, "g***i", "session-1");
        assertThat(first.snapshot().participantCount()).isEqualTo(1);
        assertThat(first.change()).get().extracting(PresenceRegistry.PresenceChange::type)
                .isEqualTo("USER_JOINED");

        assertThat(presence.join(42, 7, "g***i", "session-1").change()).isEmpty();
        assertThat(presence.join(42, 7, "g***i", "session-2").change()).isEmpty();
        PresenceRegistry.JoinResult secondUser = presence.join(42, 8, "l***a", "session-3");
        assertThat(secondUser.snapshot().participants()).containsExactly("g***i", "l***a");
        assertThat(secondUser.snapshot().participantCount()).isEqualTo(2);

        assertThat(presence.leave("session-1")).isEmpty();
        assertThat(presence.leave("session-2")).get()
                .extracting(PresenceRegistry.PresenceChange::type).isEqualTo("USER_LEFT");
        assertThat(presence.leave("session-2")).isEmpty();
        assertThat(presence.join(42, 8, "l***a", "session-3").snapshot()
                .participantCount()).isEqualTo(1);
    }

    @Test
    void sessionsOfDifferentAuctionsRemainSeparate() {
        presence.join(42, 7, "g***i", "session-1");
        PresenceRegistry.JoinResult otherAuction = presence.join(43, 7, "g***i", "session-2");

        assertThat(otherAuction.snapshot().participantCount()).isEqualTo(1);
        assertThat(presence.leave("session-1")).get()
                .extracting(PresenceRegistry.PresenceChange::auctionId).isEqualTo(42L);
        assertThat(presence.join(43, 7, "g***i", "session-2").snapshot()
                .participantCount()).isEqualTo(1);
    }
}
