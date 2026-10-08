package it.esercitazione.liveauction.producer.notifica;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.notifiche.email", name = "enabled", havingValue = "true")
public class EmailJob {
    private static final Logger LOG = LoggerFactory.getLogger(EmailJob.class);
    private final EmailQueueService queue;
    private final EmailDeliveryService delivery;
    private final EmailProperties properties;

    @EventListener(ApplicationReadyEvent.class)
    public void avvio() {
        // Recupero rapido, ma nessun SMTP nel thread di avvio.
        try {
            queue.recupera();
        } catch (RuntimeException exception) {
            LOG.warn("Recupero notifiche email non completato; nuovo tentativo al prossimo ciclo");
        }
    }

    @Scheduled(fixedDelayString = "${app.notifiche.email.interval-ms:30000}", scheduler = "emailTaskScheduler")
    public void ciclo() {
        try {
            queue.recupera();
            for (int i = 0; i < properties.getBatchSize(); i++) {
                if (!delivery.inviaProssima()) break;
            }
        } catch (RuntimeException exception) {
            LOG.warn("Job notifiche email non completato; nuovo tentativo al prossimo ciclo");
        }
    }
}
