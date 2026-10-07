package it.esercitazione.liveauction.producer.asta.schedulers;

import it.esercitazione.liveauction.producer.asta.config.AstaSchedulingConfig;
import it.esercitazione.liveauction.producer.asta.models.Stato;
import it.esercitazione.liveauction.producer.asta.repos.AstaRepository;
import it.esercitazione.liveauction.producer.asta.services.AstaLifecycleService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class AstaSchedulerTests {

    private static final Instant ADESSO = Instant.parse("2026-10-03T16:27:00Z");
    private final AstaRepository repository = mock(AstaRepository.class);
    private final AstaLifecycleService service = mock(AstaLifecycleService.class);
    private final AstaScheduler scheduler = new AstaScheduler(repository, service,
            Clock.fixed(ADESSO, ZoneOffset.UTC));

    @Test
    void selezionaAsteScaduteSenzaDuplicati() {
        when(repository.trovaIdDaAttivare(Stato.PROGRAMMATA, ADESSO.plusSeconds(180)))
                .thenReturn(List.of(1L, 2L));
        when(repository.trovaIdDaAttivare(Stato.STANZA_APERTA, ADESSO)).thenReturn(List.of(2L, 3L));
        scheduler.recuperaAllAvvio();
        verify(service).aggiornaStato(1L);
        verify(service).aggiornaStato(2L);
        verify(service).aggiornaStato(3L);
        verifyNoMoreInteractions(service);
    }

    @Test
    void erroreSuUnaAstaNonBloccaLeAltre() {
        when(repository.trovaIdDaAttivare(Stato.PROGRAMMATA, ADESSO.plusSeconds(180)))
                .thenReturn(List.of(1L, 2L));
        doThrow(new IllegalStateException("errore simulato")).when(service).aggiornaStato(1L);
        scheduler.aggiornaAste();
        verify(service).aggiornaStato(2L);
    }

    @Test
    void erroreDiLetturaVieneRitentatoAlCicloSuccessivo() {
        when(repository.trovaIdDaAttivare(Stato.PROGRAMMATA, ADESSO.plusSeconds(180)))
                .thenThrow(new IllegalStateException("errore simulato"))
                .thenReturn(List.of(1L));
        scheduler.aggiornaAste();
        verifyNoInteractions(service);
        scheduler.aggiornaAste();
        verify(service).aggiornaStato(1L);
    }

    @Test
    void configurazioneAttivaRegistraIlJob() {
        contesto().withPropertyValues("app.aste.scheduler.interval-ms=3600000").run(context -> {
            assertThat(context).hasSingleBean(AstaScheduler.class);
            assertThat(context.getBean(ScheduledAnnotationBeanPostProcessor.class).getScheduledTasks()).hasSize(1);
        });
    }

    @Test
    void configurazioneDisattivataNonAvviaJob() {
        contesto().withPropertyValues("app.aste.scheduler.enabled=false").run(context -> {
            assertThat(context).doesNotHaveBean(AstaScheduler.class);
            assertThat(context).doesNotHaveBean(ScheduledAnnotationBeanPostProcessor.class);
        });
    }

    private ApplicationContextRunner contesto() {
        return new ApplicationContextRunner()
                .withBean(AstaRepository.class, () -> repository)
                .withBean(AstaLifecycleService.class, () -> service)
                .withUserConfiguration(AstaSchedulingConfig.class, AstaScheduler.class);
    }
}
