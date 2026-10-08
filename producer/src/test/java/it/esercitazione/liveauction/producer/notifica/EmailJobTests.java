package it.esercitazione.liveauction.producer.notifica;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.*;

class EmailJobTests {
    @Test
    void startupOnlyRecoversAndPeriodicCycleRespectsBatchLimit() {
        var queue = mock(EmailQueueService.class);
        var delivery = mock(EmailDeliveryService.class);
        var properties = new EmailProperties();
        properties.setBatchSize(2);
        when(delivery.inviaProssima()).thenReturn(true);
        var job = new EmailJob(queue, delivery, properties);
        job.avvio();
        verifyNoInteractions(delivery);
        job.ciclo();
        verify(queue, times(2)).recupera();
        verify(delivery, times(2)).inviaProssima();
    }

    @Test
    void failureDoesNotStopApplicationAndLaterCycleRetries() {
        var queue = mock(EmailQueueService.class);
        var delivery = mock(EmailDeliveryService.class);
        var job = new EmailJob(queue, delivery, new EmailProperties());
        when(queue.recupera()).thenThrow(new IllegalStateException("Database error")).thenReturn(1);
        assertThatCode(job::avvio).doesNotThrowAnyException();
        assertThatCode(job::ciclo).doesNotThrowAnyException();
        verify(delivery).inviaProssima();
    }
}
