package it.esercitazione.liveauction.producer.asta.schedulers;

import org.slf4j.Logger;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/** Limita solo i log ripetuti: non sospende i retry e non modifica il dominio. */
final class SchedulerFailureLog {
    private static final Duration INTERVALLO = Duration.ofSeconds(60);
    private final Logger logger;
    private final Clock clock;
    private final Map<String, Errore> errori = new HashMap<>();

    SchedulerFailureLog(Logger logger, Clock clock) {
        this.logger = logger;
        this.clock = clock;
    }

    synchronized void fallimento(String operazione, String descrizione, RuntimeException exception) {
        Instant adesso = clock.instant();
        Errore precedente = errori.get(operazione);
        long tentativi = precedente == null ? 1 : precedente.tentativi() + 1;
        boolean segnala = precedente == null || !adesso.isBefore(precedente.ultimaSegnalazione().plus(INTERVALLO));
        errori.put(operazione, new Errore(segnala ? adesso : precedente.ultimaSegnalazione(), tentativi));
        if (segnala) {
            logger.warn("{}; {} tentativi falliti. Retry al prossimo ciclo; stack trace limitato a uno ogni 60 secondi per operazione",
                    descrizione, tentativi, exception);
        }
    }

    synchronized void successo(String operazione) {
        Errore precedente = errori.remove(operazione);
        if (precedente != null) {
            logger.info("Operazione scheduler {} recuperata dopo {} tentativi falliti", operazione, precedente.tentativi());
        }
    }

    private record Errore(Instant ultimaSegnalazione, long tentativi) {}
}
