package it.esercitazione.liveauction.producer.asta.services;

import it.esercitazione.liveauction.producer.asta.events.AstaTransizioneEvent;
import it.esercitazione.liveauction.producer.asta.events.AstaTransizioneEvent.Tipo;
import it.esercitazione.liveauction.producer.asta.models.Asta;
import it.esercitazione.liveauction.producer.asta.models.Stato;
import it.esercitazione.liveauction.producer.asta.repos.AstaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.Instant;

@Service
public class AstaLifecycleService {

    private final AstaRepository astaRepository;
    private final ApplicationEventPublisher publisher;
    private final Clock clock;

    @Autowired
    public AstaLifecycleService(AstaRepository astaRepository, ApplicationEventPublisher publisher) {
        this(astaRepository, publisher, Clock.systemUTC());
    }

    AstaLifecycleService(AstaRepository astaRepository, ApplicationEventPublisher publisher, Clock clock) {
        this.astaRepository = astaRepository;
        this.publisher = publisher;
        this.clock = clock;
    }

    /** Operazione interna del motore temporale; non esposta tramite REST. */
    @Transactional
    public int aggiornaStato(long astaId) {
        Asta asta = astaRepository.trovaConLock(astaId).orElse(null);
        if (asta == null) {
            return 0;
        }

        // Leggere il tempo dopo l'attesa del lock evita decisioni basate su un istante vecchio.
        Instant adesso = clock.instant();
        int transizioni = 0;
        if (asta.getStato() == Stato.PROGRAMMATA
                && !adesso.isBefore(asta.getInizioAt().minusSeconds(3 * 60))) {
            asta.setStato(Stato.STANZA_APERTA);
            registraTransizione(asta, Tipo.ROOM_OPENED, adesso);
            transizioni++;
        }
        if (asta.getStato() == Stato.STANZA_APERTA && !adesso.isBefore(asta.getInizioAt())) {
            asta.setStato(Stato.APERTA);
            asta.setFineAt(asta.getInizioAt().plusSeconds(7 * 60));
            registraTransizione(asta, Tipo.AUCTION_STARTED, adesso);
            transizioni++;
        }
        return transizioni;
    }

    private void registraTransizione(Asta asta, Tipo tipo, Instant adesso) {
        asta.setSequence(asta.getSequence() + 1);
        AstaTransizioneEvent evento = new AstaTransizioneEvent(tipo, asta.getId(), asta.getSequence(),
                adesso, asta.getStato(), asta.getInizioAt().minusSeconds(3 * 60),
                asta.getInizioAt(), asta.getFineAt());

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                publisher.publishEvent(evento);
            }
        });
    }
}
