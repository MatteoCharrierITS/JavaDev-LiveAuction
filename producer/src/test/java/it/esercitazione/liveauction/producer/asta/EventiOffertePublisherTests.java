package it.esercitazione.liveauction.producer.asta;

import it.esercitazione.liveauction.producer.asta.events.EventiOffertePublisher;
import it.esercitazione.liveauction.producer.asta.events.EventoOfferte;
import it.esercitazione.liveauction.producer.asta.responses.StatoOfferteResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class EventiOffertePublisherTests {
    private final ApplicationEventPublisher spring = mock(ApplicationEventPublisher.class);
    private final EventiOffertePublisher publisher = new EventiOffertePublisher(spring);
    private final EventoOfferte evento = new EventoOfferte("AUCTION_CLOSED",
            new StatoOfferteResponse(1, 2, "CHIUSA", null, null, null, 0,
                    Instant.EPOCH, null, 1, Instant.EPOCH), null, null, 0);

    @AfterEach
    void pulisci() {
        TransactionSynchronizationManager.clear();
    }

    @Test
    void publishOnlyAfterSuccessfulCommit() {
        transazione();
        publisher.dopoCommit(evento);
        verifyNoInteractions(spring);
        TransactionSynchronizationManager.getSynchronizations().getFirst().afterCommit();
        verify(spring).publishEvent(evento);
    }

    @Test
    void rollbackDoesNotPublish() {
        transazione();
        publisher.dopoCommit(evento);
        TransactionSynchronizationManager.getSynchronizations().getFirst()
                .afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
        verifyNoInteractions(spring);
    }

    @Test
    void transportFailureCannotUndoCommittedSettlement() {
        transazione();
        doThrow(new IllegalStateException("Trasporto di prova non disponibile")).when(spring).publishEvent(evento);
        publisher.dopoCommit(evento);
        assertThatCode(() -> TransactionSynchronizationManager.getSynchronizations().getFirst().afterCommit())
                .doesNotThrowAnyException();
    }

    @Test
    void publishingOutsideTransactionIsRejected() {
        assertThatThrownBy(() -> publisher.dopoCommit(evento)).isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(spring);
    }

    private static void transazione() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.initSynchronization();
    }
}
