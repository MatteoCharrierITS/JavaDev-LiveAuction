package it.esercitazione.liveauction.producer.asta.events;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
@RequiredArgsConstructor
public class EventiOffertePublisher {
    private static final Logger LOG = LoggerFactory.getLogger(EventiOffertePublisher.class);
    private final ApplicationEventPublisher eventi;

    public void dopoCommit(EventoOfferte evento) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || !TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new IllegalStateException("Gli eventi offerte richiedono una transazione");
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    eventi.publishEvent(evento);
                } catch (RuntimeException exception) {
                    // Il settlement è già definitivo; gli errori di trasporto non lo annullano.
                    LOG.error("Invio evento {} per asta {} fallito dopo il commit",
                            evento.type(), evento.stato().auctionId(), exception);
                }
            }
        });
    }
}
