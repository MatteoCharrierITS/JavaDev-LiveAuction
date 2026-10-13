package it.esercitazione.liveauction.producer.asta.services;

import it.esercitazione.liveauction.producer.asta.events.AstaTransizioneEvent;
import it.esercitazione.liveauction.producer.asta.exceptions.AstaException;
import it.esercitazione.liveauction.producer.asta.models.Stato;
import it.esercitazione.liveauction.producer.asta.repos.AnnullamentoAstaRepository;
import it.esercitazione.liveauction.producer.asta.repos.AnnullamentoAstaRepository.AstaDaAnnullare;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AnnullamentoAstaServiceTests {
    private final Instant inizio = Instant.parse("2026-10-12T12:00:00Z");
    private final AnnullamentoAstaRepository repository = mock(AnnullamentoAstaRepository.class);
    private final ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);

    @BeforeEach void prepara() { TransactionSynchronizationManager.initSynchronization(); }
    @AfterEach void pulisci() { TransactionSynchronizationManager.clearSynchronization(); }

    @Test
    void leggeTempoDopoEntrambiILockEAllaScadenzaNonAnnulla() {
        var asta = asta(Stato.APERTA);
        var clock = mock(Clock.class);
        when(clock.instant()).thenReturn(asta.fineAt());
        assertThatThrownBy(() -> new AnnullamentoAstaService(repository, publisher, clock).annulla(1))
                .isInstanceOf(AstaException.class);
        var ordine = inOrder(repository, clock);
        ordine.verify(repository).blocca(1);
        ordine.verify(repository).haOfferte(1);
        ordine.verify(repository).bloccaProdotto(asta);
        ordine.verify(clock).instant();
        verify(repository, never()).annulla(any(), any());
        verifyNoInteractions(publisher);
    }

    @Test
    void unNanosecondoPrimaDellaScadenzaAnnullaConPayloadCompletoSoloDopoCommit() {
        var asta = asta(Stato.APERTA);
        Instant now = asta.fineAt().minusNanos(1);
        var risultato = servizio(now).annulla(1);
        assertThat(risultato.annullataAt()).isEqualTo(now);
        assertThat(risultato.sequence()).isEqualTo(8);
        verifyNoInteractions(publisher);
        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        var captor = ArgumentCaptor.forClass(AstaTransizioneEvent.class);
        verify(publisher).publishEvent(captor.capture());
        var evento = captor.getValue();
        assertThat(evento.type()).isEqualTo(AstaTransizioneEvent.Tipo.AUCTION_CANCELLED);
        assertThat(evento.stato()).isEqualTo(Stato.ANNULLATA);
        assertThat(evento.sequence()).isEqualTo(8);
        assertThat(evento.serverTime()).isEqualTo(now);
        assertThat(evento.aperturaStanzaAt()).isEqualTo(inizio.minusSeconds(180));
        assertThat(evento.inizioAt()).isEqualTo(inizio);
        assertThat(evento.fineAt()).isEqualTo(asta.fineAt());
    }

    @Test
    void erroreTrasportoDopoCommitNonTrasformaAnnullamentoInFallimento() {
        asta(Stato.APERTA);
        doThrow(new IllegalStateException("Trasporto non disponibile")).when(publisher).publishEvent(any(Object.class));
        servizio(inizio).annulla(1);
        assertThatCode(() -> TransactionSynchronizationManager.getSynchronizations()
                .forEach(TransactionSynchronization::afterCommit)).doesNotThrowAnyException();
    }

    @Test
    void retryAnnullataNonToccaProdottoOTempoENonRegistraEvento() {
        var asta = asta(Stato.ANNULLATA);
        assertThat(servizio(inizio.plusSeconds(900)).annulla(1)).isEqualTo(asta.esito());
        verify(repository, never()).haOfferte(anyLong());
        verify(repository, never()).bloccaProdotto(any());
        assertThat(TransactionSynchronizationManager.getSynchronizations()).isEmpty();
    }

    private AstaDaAnnullare asta(Stato stato) {
        var asta = new AstaDaAnnullare(1, 2, stato, inizio, inizio.plusSeconds(420), 7,
                stato == Stato.ANNULLATA ? inizio : null);
        when(repository.blocca(1)).thenReturn(asta);
        return asta;
    }
    private AnnullamentoAstaService servizio(Instant now) {
        return new AnnullamentoAstaService(repository, publisher, Clock.fixed(now, ZoneOffset.UTC));
    }
}
