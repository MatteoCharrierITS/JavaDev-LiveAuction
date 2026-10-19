package it.esercitazione.liveauction.producer.asta.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.Duration;

@Configuration
public class OfferteConfig {
    @Bean("clockOfferte")
    Clock clockOfferte() {
        // PostgreSQL conserva i microsecondi: su Linux/macOS l'orologio ha nanosecondi e le risposte
        // idempotenti (rilette dal database) differirebbero da quelle della prima esecuzione.
        return Clock.tick(Clock.systemUTC(), Duration.ofNanos(1000));
    }
}
