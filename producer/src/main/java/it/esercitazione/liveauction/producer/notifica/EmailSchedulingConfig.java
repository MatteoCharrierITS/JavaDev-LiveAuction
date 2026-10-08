package it.esercitazione.liveauction.producer.notifica;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

@Configuration
@ConditionalOnProperty(prefix = "app.notifiche.email", name = "enabled", havingValue = "true")
public class EmailSchedulingConfig {
    @Bean(destroyMethod = "shutdown")
    public ScheduledExecutorService emailTaskScheduler() {
        // SMTP non deve occupare il thread dei job temporali delle aste o degli heartbeat STOMP.
        return Executors.newSingleThreadScheduledExecutor(task -> {
            var thread = new Thread(task, "email-delivery");
            thread.setDaemon(true);
            return thread;
        });
    }
}
