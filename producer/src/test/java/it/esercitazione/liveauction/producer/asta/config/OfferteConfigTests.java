package it.esercitazione.liveauction.producer.asta.config;

import org.junit.jupiter.api.Test;

import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

class OfferteConfigTests {
    @Test
    void orologioDelleAsteHaLaPrecisioneDiPostgresql() {
        var clock = OfferteConfig.orologioAste();
        for (int i = 0; i < 1_000; i++) {
            var istante = clock.instant();
            assertThat(istante).isEqualTo(istante.truncatedTo(ChronoUnit.MICROS));
        }
        assertThat(new OfferteConfig().clockOfferte().getZone()).isEqualTo(java.time.ZoneOffset.UTC);
    }
}
