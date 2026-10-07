package it.esercitazione.liveauction.producer.asta.schedulers;

import it.esercitazione.liveauction.producer.asta.models.Stato;
import it.esercitazione.liveauction.producer.asta.repos.AstaRepository;
import it.esercitazione.liveauction.producer.asta.services.AstaLifecycleService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;

@Component
@ConditionalOnProperty(prefix = "app.aste.scheduler", name = "enabled", havingValue = "true", matchIfMissing = true)
public class AstaScheduler {

    private static final Logger log = LoggerFactory.getLogger(AstaScheduler.class);

    private final AstaRepository astaRepository;
    private final AstaLifecycleService lifecycleService;
    private final Clock clock;

    @Autowired
    public AstaScheduler(AstaRepository astaRepository, AstaLifecycleService lifecycleService) {
        this(astaRepository, lifecycleService, Clock.systemUTC());
    }

    AstaScheduler(AstaRepository astaRepository, AstaLifecycleService lifecycleService, Clock clock) {
        this.astaRepository = astaRepository;
        this.lifecycleService = lifecycleService;
        this.clock = clock;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void recuperaAllAvvio() {
        aggiornaAste();
    }

    @Scheduled(fixedDelayString = "${app.aste.scheduler.interval-ms:1000}")
    public void aggiornaAste() {
        Instant adesso = clock.instant();
        Set<Long> ids;
        try {
            ids = new LinkedHashSet<>(astaRepository.trovaIdDaAttivare(Stato.PROGRAMMATA, adesso.plusSeconds(180)));
            ids.addAll(astaRepository.trovaIdDaAttivare(Stato.STANZA_APERTA, adesso));
        } catch (RuntimeException exception) {
            log.warn("Impossibile leggere le aste da attivare; nuovo tentativo al prossimo ciclo", exception);
            return;
        }

        // Il servizio apre una transazione distinta per ogni asta e ricontrolla lo stato sotto lock.
        for (long id : ids) {
            try {
                lifecycleService.aggiornaStato(id);
            } catch (RuntimeException exception) {
                log.warn("Transizione dell'asta {} fallita; nuovo tentativo al prossimo ciclo", id, exception);
            }
        }
    }
}
