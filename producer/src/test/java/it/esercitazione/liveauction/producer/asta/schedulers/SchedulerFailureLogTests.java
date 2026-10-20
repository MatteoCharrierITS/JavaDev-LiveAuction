package it.esercitazione.liveauction.producer.asta.schedulers;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;

import java.time.Clock;
import java.time.Instant;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SchedulerFailureLogTests {
    private final Logger logger = mock(Logger.class);
    private final Clock clock = mock(Clock.class);
    private final RuntimeException errore = new IllegalStateException("stock incoerente");
    private final SchedulerFailureLog reporter = new SchedulerFailureLog(logger, clock);
    private final Instant inizio = Instant.parse("2026-10-20T10:00:00Z");

    @Test
    void segnalaSubitoEPoiOgniSessantaSecondiContandoTuttiIFallimenti() {
        when(clock.instant()).thenReturn(inizio, inizio.plusSeconds(1), inizio.plusSeconds(59), inizio.plusSeconds(60));
        for (int i = 0; i < 4; i++) reporter.fallimento("chiusura-1", "Chiusura 1", errore);
        verify(logger).warn(anyString(), eq("Chiusura 1"), eq(1L), same(errore));
        verify(logger).warn(anyString(), eq("Chiusura 1"), eq(4L), same(errore));
        verifyNoMoreInteractions(logger);
    }

    @Test
    void erroriDiAsteDiverseSonoSegnalatiSeparatamente() {
        when(clock.instant()).thenReturn(inizio);
        reporter.fallimento("chiusura-1", "Chiusura 1", errore);
        reporter.fallimento("chiusura-2", "Chiusura 2", errore);
        verify(logger).warn(anyString(), eq("Chiusura 1"), eq(1L), same(errore));
        verify(logger).warn(anyString(), eq("Chiusura 2"), eq(1L), same(errore));
        verifyNoMoreInteractions(logger);
    }

    @Test
    void recuperoAzzeraIlContatoreENuovoErroreVieneSegnalatoSubito() {
        when(clock.instant()).thenReturn(inizio);
        reporter.fallimento("chiusura-1", "Chiusura 1", errore);
        reporter.successo("chiusura-1");
        reporter.successo("chiusura-1");
        reporter.fallimento("chiusura-1", "Chiusura 1", errore);
        verify(logger, times(2)).warn(anyString(), eq("Chiusura 1"), eq(1L), same(errore));
        verify(logger).info(anyString(), eq("chiusura-1"), eq(1L));
        verifyNoMoreInteractions(logger);
    }
}
