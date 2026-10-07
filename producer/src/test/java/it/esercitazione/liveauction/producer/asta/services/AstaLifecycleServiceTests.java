package it.esercitazione.liveauction.producer.asta.services;

import it.esercitazione.liveauction.producer.asta.events.AstaTransizioneEvent;
import it.esercitazione.liveauction.producer.asta.events.AstaTransizioneEvent.Tipo;
import it.esercitazione.liveauction.producer.asta.models.Asta;
import it.esercitazione.liveauction.producer.asta.models.Stato;
import it.esercitazione.liveauction.producer.asta.repos.AstaRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class AstaLifecycleServiceTests {

    private static final Instant INIZIO = Instant.parse("2026-10-03T16:30:00Z");
    private final AstaRepository repository = mock(AstaRepository.class);
    private final ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);

    @BeforeEach
    void preparaSincronizzazione() {
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);
    }

    @AfterEach
    void pulisciSincronizzazione() {
        TransactionSynchronizationManager.clearSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    @Test
    void nonAprePrimaDeiTreMinuti() {
        Asta asta = asta(Stato.PROGRAMMATA, 0);
        assertThat(servizio(INIZIO.minusSeconds(180).minusNanos(1)).aggiornaStato(1L)).isZero();
        assertThat(asta.getStato()).isEqualTo(Stato.PROGRAMMATA);
        assertThat(asta.getSequence()).isZero();
        assertThat(TransactionSynchronizationManager.getSynchronizations()).isEmpty();
    }

    @Test
    void apreStanzaEsattamenteATreMinutiEPubblicaDopoCommit() {
        Asta asta = asta(Stato.PROGRAMMATA, 0);
        assertThat(servizio(INIZIO.minusSeconds(180)).aggiornaStato(1L)).isEqualTo(1);
        assertThat(asta.getStato()).isEqualTo(Stato.STANZA_APERTA);
        assertThat(asta.getSequence()).isEqualTo(1);
        verifyNoInteractions(publisher);

        committa();
        var captor = ArgumentCaptor.forClass(AstaTransizioneEvent.class);
        verify(publisher).publishEvent(captor.capture());
        assertThat(captor.getValue().type()).isEqualTo(Tipo.ROOM_OPENED);
        assertThat(captor.getValue().auctionId()).isEqualTo(1L);
        assertThat(captor.getValue().sequence()).isEqualTo(1);
        assertThat(captor.getValue().stato()).isEqualTo(Stato.STANZA_APERTA);
    }

    @Test
    void nonAvviaPrimaDellInizio() {
        Asta asta = asta(Stato.STANZA_APERTA, 1);
        assertThat(servizio(INIZIO.minusNanos(1)).aggiornaStato(1L)).isZero();
        assertThat(asta.getStato()).isEqualTo(Stato.STANZA_APERTA);
        assertThat(asta.getSequence()).isEqualTo(1);
    }

    @Test
    void avviaAllInizioConSetteMinuti() {
        Asta asta = asta(Stato.STANZA_APERTA, 1);
        assertThat(servizio(INIZIO).aggiornaStato(1L)).isEqualTo(1);
        assertThat(asta.getStato()).isEqualTo(Stato.APERTA);
        assertThat(asta.getFineAt()).isEqualTo(INIZIO.plusSeconds(420));
        assertThat(asta.getSequence()).isEqualTo(2);
        verifyNoInteractions(publisher);
        committa();
        var captor = ArgumentCaptor.forClass(AstaTransizioneEvent.class);
        verify(publisher).publishEvent(captor.capture());
        assertThat(captor.getValue().type()).isEqualTo(Tipo.AUCTION_STARTED);
        assertThat(captor.getValue().serverTime()).isEqualTo(INIZIO);
    }

    @Test
    void riavvioRecuperaEntrambeLeTransizioniInOrdine() {
        Asta asta = asta(Stato.PROGRAMMATA, 0);
        assertThat(servizio(INIZIO.plusSeconds(120)).aggiornaStato(1L)).isEqualTo(2);
        assertThat(asta.getStato()).isEqualTo(Stato.APERTA);
        assertThat(asta.getFineAt()).isEqualTo(INIZIO.plusSeconds(420));
        verifyNoInteractions(publisher);
        committa();

        var captor = ArgumentCaptor.forClass(AstaTransizioneEvent.class);
        verify(publisher, times(2)).publishEvent(captor.capture());
        assertThat(captor.getAllValues()).extracting(AstaTransizioneEvent::type)
                .containsExactly(Tipo.ROOM_OPENED, Tipo.AUCTION_STARTED);
        assertThat(captor.getAllValues()).extracting(AstaTransizioneEvent::sequence).containsExactly(1L, 2L);
        assertThat(captor.getAllValues().getFirst().stato()).isEqualTo(Stato.STANZA_APERTA);
        assertThat(captor.getAllValues().getLast().stato()).isEqualTo(Stato.APERTA);
    }

    @Test
    void riavvioDopoScadenzaNonRegalaAltriSetteMinuti() {
        Asta asta = asta(Stato.PROGRAMMATA, 0);
        Instant adesso = INIZIO.plusSeconds(600);
        assertThat(servizio(adesso).aggiornaStato(1L)).isEqualTo(2);
        assertThat(asta.getFineAt()).isBefore(adesso).isEqualTo(INIZIO.plusSeconds(420));
    }

    @Test
    void ripetereIlCicloNonDuplicaLaTransizione() {
        Asta asta = asta(Stato.PROGRAMMATA, 0);
        AstaLifecycleService service = servizio(INIZIO.minusSeconds(180));
        assertThat(service.aggiornaStato(1L)).isEqualTo(1);
        assertThat(service.aggiornaStato(1L)).isZero();
        assertThat(asta.getSequence()).isEqualTo(1);
        committa();
        verify(publisher, times(1)).publishEvent(any(AstaTransizioneEvent.class));
    }

    @ParameterizedTest
    @EnumSource(value = Stato.class, names = {"APERTA", "CHIUSA", "ANNULLATA"})
    void nonModificaStatiSuccessiviONuoveScadenze(Stato stato) {
        Asta asta = asta(stato, 5);
        asta.setFineAt(INIZIO.plusSeconds(800));
        assertThat(servizio(INIZIO.plusSeconds(120)).aggiornaStato(1L)).isZero();
        assertThat(asta.getStato()).isEqualTo(stato);
        assertThat(asta.getSequence()).isEqualTo(5);
        assertThat(asta.getFineAt()).isEqualTo(INIZIO.plusSeconds(800));
    }

    @Test
    void ignoraAstaNonPiuEsistente() {
        when(repository.trovaConLock(1L)).thenReturn(Optional.empty());
        assertThat(servizio(INIZIO).aggiornaStato(1L)).isZero();
        verifyNoInteractions(publisher);
    }

    private Asta asta(Stato stato, long sequence) {
        Asta asta = new Asta();
        asta.setId(1L);
        asta.setStato(stato);
        asta.setSequence(sequence);
        asta.setInizioAt(INIZIO);
        asta.setFineAt(INIZIO.plusSeconds(420));
        when(repository.trovaConLock(1L)).thenReturn(Optional.of(asta));
        return asta;
    }

    private AstaLifecycleService servizio(Instant adesso) {
        return new AstaLifecycleService(repository, publisher, Clock.fixed(adesso, ZoneOffset.UTC));
    }

    private static void committa() {
        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
    }
}
