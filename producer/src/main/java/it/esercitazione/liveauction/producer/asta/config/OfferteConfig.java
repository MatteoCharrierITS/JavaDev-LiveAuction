package it.esercitazione.liveauction.producer.asta.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class OfferteConfig {
    @Bean("clockOfferte")
    Clock clockOfferte() {
        return Clock.systemUTC();
    }
}
