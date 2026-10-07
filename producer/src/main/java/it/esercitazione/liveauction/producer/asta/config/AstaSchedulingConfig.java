package it.esercitazione.liveauction.producer.asta.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(prefix = "app.aste.scheduler", name = "enabled", havingValue = "true", matchIfMissing = true)
public class AstaSchedulingConfig {
}
