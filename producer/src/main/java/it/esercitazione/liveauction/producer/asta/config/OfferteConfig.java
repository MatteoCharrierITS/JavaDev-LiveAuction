package it.esercitazione.liveauction.producer.asta.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.Duration;
import java.time.temporal.ChronoUnit;

@Configuration
public class OfferteConfig {
    /**
     * PostgreSQL conserva i timestamp al microsecondo: un orologio con nanosecondi (Linux)
     * restituirebbe nelle risposte istanti diversi da quelli salvati.
     */
    public static Clock orologioAste() {
        return Clock.tick(Clock.systemUTC(), Duration.of(1, ChronoUnit.MICROS));
    }

    @Bean("clockOfferte")
    Clock clockOfferte() {
        return orologioAste();
    }
}
