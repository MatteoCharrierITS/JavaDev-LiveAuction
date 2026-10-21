package it.esercitazione.liveauction.producer.asta.schedulers;

import it.esercitazione.liveauction.producer.asta.config.OfferteConfig;
import it.esercitazione.liveauction.producer.asta.models.Stato;
import it.esercitazione.liveauction.producer.asta.repos.AstaRepository;
import it.esercitazione.liveauction.producer.asta.services.AstaLifecycleService;
import it.esercitazione.liveauction.producer.asta.services.ChiusuraAstaService;
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
    private final ChiusuraAstaService chiusuraAstaService;
    private final Clock clock;
    private final SchedulerFailureLog errori;

    @Autowired
    public AstaScheduler(AstaRepository astaRepository, AstaLifecycleService lifecycleService,
                         ChiusuraAstaService chiusuraAstaService) {
        this(astaRepository, lifecycleService, chiusuraAstaService, OfferteConfig.orologioAste());
    }

    AstaScheduler(AstaRepository astaRepository, AstaLifecycleService lifecycleService,
                  ChiusuraAstaService chiusuraAstaService, Clock clock) {
        this.astaRepository = astaRepository;
        this.lifecycleService = lifecycleService;
        this.chiusuraAstaService = chiusuraAstaService;
        this.clock = clock;
        this.errori = new SchedulerFailureLog(log, clock);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void recuperaAllAvvio() {
        aggiornaAste();
    }

    @Scheduled(fixedDelayString = "${app.aste.scheduler.interval-ms:1000}")
    public void aggiornaAste() {
        aggiornaStatiInScadenza();
        chiudiAsteScadute();
    }

    private void aggiornaStatiInScadenza() {
        Instant adesso = clock.instant();
        Set<Long> ids = new LinkedHashSet<>();
        try {
            ids = new LinkedHashSet<>(astaRepository.trovaIdDaAttivare(Stato.PROGRAMMATA, adesso.plusSeconds(180)));
            errori.successo("lettura-programmate");
        } catch (RuntimeException exception) {
            errori.fallimento("lettura-programmate", "Impossibile leggere le aste PROGRAMMATA da attivare", exception);
        }
        try {
            ids.addAll(astaRepository.trovaIdDaAttivare(Stato.STANZA_APERTA, adesso));
            errori.successo("lettura-stanze");
        } catch (RuntimeException exception) {
            errori.fallimento("lettura-stanze", "Impossibile leggere le aste STANZA_APERTA da attivare", exception);
        }

        // Il servizio apre una transazione distinta per ogni asta e ricontrolla lo stato sotto lock.
        for (long id : ids) {
            try {
                lifecycleService.aggiornaStato(id);
                errori.successo("transizione-" + id);
            } catch (RuntimeException exception) {
                errori.fallimento("transizione-" + id, "Transizione dell'asta " + id + " fallita", exception);
            }
        }
    }

    private void chiudiAsteScadute() {
        Set<Long> ids;
        try {
            ids = new LinkedHashSet<>(astaRepository.trovaIdDaChiudere(Stato.APERTA, clock.instant()));
            errori.successo("lettura-chiusure");
        } catch (RuntimeException exception) {
            errori.fallimento("lettura-chiusure", "Impossibile leggere le aste scadute", exception);
            return;
        }

        // Il settlement apre una transazione distinta e ricontrolla stato e scadenza sotto lock.
        for (long id : ids) {
            try {
                chiusuraAstaService.chiudi(id);
                errori.successo("chiusura-" + id);
            } catch (RuntimeException exception) {
                errori.fallimento("chiusura-" + id, "Chiusura dell'asta " + id + " fallita", exception);
            }
        }
    }
}
