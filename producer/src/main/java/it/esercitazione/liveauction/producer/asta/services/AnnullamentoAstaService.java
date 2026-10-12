package it.esercitazione.liveauction.producer.asta.services;

import it.esercitazione.liveauction.producer.asta.events.AstaTransizioneEvent;
import it.esercitazione.liveauction.producer.asta.exceptions.AstaException;
import it.esercitazione.liveauction.producer.asta.models.Stato;
import it.esercitazione.liveauction.producer.asta.repos.AnnullamentoAstaRepository;
import it.esercitazione.liveauction.producer.asta.responses.AnnullamentoAstaResponse;
import jakarta.validation.constraints.Positive;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.validation.annotation.Validated;

import java.time.Clock;
import java.time.Instant;

@Service
@Validated
public class AnnullamentoAstaService {
    private static final Logger LOG = LoggerFactory.getLogger(AnnullamentoAstaService.class);
    private final AnnullamentoAstaRepository repository;
    private final ApplicationEventPublisher publisher;
    private final Clock clock;

    public AnnullamentoAstaService(AnnullamentoAstaRepository repository, ApplicationEventPublisher publisher,
                                  @Qualifier("clockOfferte") Clock clock) {
        this.repository = repository;
        this.publisher = publisher;
        this.clock = clock;
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public AnnullamentoAstaResponse annulla(@Positive long astaId) {
        var asta = repository.blocca(astaId);
        if (asta.stato() == Stato.ANNULLATA) {
            return asta.esito();
        }
        if (asta.stato() == Stato.CHIUSA) {
            throw new AstaException(HttpStatus.CONFLICT, "ASTA_NON_ANNULLABILE", "L'asta è già conclusa");
        }
        // Anche le offerte ritirate sono offerte ricevute: non ampliare la regola concordata.
        if (repository.haOfferte(astaId)) {
            throw new AstaException(HttpStatus.CONFLICT, "ASTA_CON_OFFERTE",
                    "Non è possibile annullare un'asta che ha ricevuto offerte");
        }
        repository.bloccaProdotto(asta);
        Instant now = clock.instant();
        if (!now.isBefore(asta.fineAt())) {
            throw new AstaException(HttpStatus.CONFLICT, "ASTA_NON_ANNULLABILE", "L'asta è già scaduta");
        }
        repository.annulla(asta, now);
        var evento = new AstaTransizioneEvent(AstaTransizioneEvent.Tipo.AUCTION_CANCELLED,
                astaId, asta.sequence() + 1, now, Stato.ANNULLATA, asta.inizioAt().minusSeconds(180),
                asta.inizioAt(), asta.fineAt());
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    publisher.publishEvent(evento);
                } catch (RuntimeException exception) {
                    LOG.error("Invio annullamento per asta {} fallito dopo il commit", astaId, exception);
                }
            }
        });
        return new AnnullamentoAstaResponse(astaId, Stato.ANNULLATA, now, evento.sequence());
    }
}
