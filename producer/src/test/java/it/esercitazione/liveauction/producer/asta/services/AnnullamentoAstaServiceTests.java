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

import it.esercitazione.liveauction.producer.portafoglio.model.TipoMovimento;
import it.esercitazione.liveauction.producer.portafoglio.repos.PortafoglioRepository;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AnnullamentoAstaServiceTests {
    private final Instant inizio = Instant.parse("2026-10-12T12:00:00Z");
    private final AnnullamentoAstaRepository repository = mock(AnnullamentoAstaRepository.class);
    private final ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
    private final PortafoglioRepository portafogli = mock(PortafoglioRepository.class);

    @BeforeEach void prepara() { TransactionSynchronizationManager.initSynchronization(); }
    @AfterEach void pulisci() { TransactionSynchronizationManager.clearSynchronization(); }

    @Test
    void leggeTempoDopoEntrambiILockEAllaScadenzaNonAnnulla() {
        var asta = asta(Stato.APERTA);
        var clock = mock(Clock.class);
        when(clock.instant()).thenReturn(asta.fineAt());
        assertThatThrownBy(() -> new AnnullamentoAstaService(repository, portafogli, publisher, clock).annulla(1))
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
    void conOfferteNonScadutaRifiutaSenzaBloccarePortafogliOProdotto() {
        asta(Stato.APERTA);
        when(repository.haOfferte(1)).thenReturn(true);
        assertThatThrownBy(() -> servizio(inizio.plusSeconds(419)).annulla(1))
                .isInstanceOfSatisfying(AstaException.class, e -> assertThat(e.getBody().getProperties())
                        .containsEntry("code", "ASTA_CON_OFFERTE"));
        verifyNoInteractions(portafogli);
        verify(repository, never()).bloccaProdotto(any());
        verify(repository, never()).annulla(any(), any());
    }

    @Test
    void scadutaConOfferteRilasciaRiservaDelLeaderBloccandoPortafoglioPrimaDelProdotto() {
        var asta = asta(Stato.APERTA);
        Instant now = asta.fineAt().plus(AnnullamentoAstaService.GRAZIA_CHIUSURA);
        when(repository.haOfferte(1)).thenReturn(true);
        when(repository.leader(1)).thenReturn(Optional.of(
                new AnnullamentoAstaRepository.LeaderDaRilasciare(5, new BigDecimal("120.00"))));
        var saldo = new PortafoglioRepository.PortafoglioBloccato(50, 5, new BigDecimal("500"), new BigDecimal("120"));
        when(portafogli.bloccaPortafogli(List.of(5L))).thenReturn(Map.of(5L, saldo));
        var risultato = servizio(now).annulla(1);
        assertThat(risultato.stato()).isEqualTo(Stato.ANNULLATA);
        var ordine = inOrder(repository, portafogli);
        ordine.verify(repository).blocca(1);
        ordine.verify(repository).haOfferte(1);
        ordine.verify(portafogli).bloccaPortafogli(List.of(5L));
        ordine.verify(repository).bloccaProdotto(asta);
        ordine.verify(portafogli).movimenta(saldo, 1L, TipoMovimento.RILASCIO_OFFERTA, new BigDecimal("120.00"),
                BigDecimal.ZERO, new BigDecimal("-120.00"), now);
        ordine.verify(repository).annulla(asta, now);
    }

    @Test
    void scadutaConOfferteDentroLaGraziaRifiutaConChiusuraInCorso() {
        var asta = asta(Stato.APERTA);
        when(repository.haOfferte(1)).thenReturn(true);
        for (Instant now : List.of(asta.fineAt(), asta.fineAt().plus(AnnullamentoAstaService.GRAZIA_CHIUSURA)
                .minusNanos(1000))) {
            assertThatThrownBy(() -> servizio(now).annulla(1))
                    .isInstanceOfSatisfying(AstaException.class, e -> assertThat(e.getBody().getProperties())
                            .containsEntry("code", "CHIUSURA_IN_CORSO"));
        }
        verifyNoInteractions(portafogli);
        verify(repository, never()).leader(anyLong());
        verify(repository, never()).bloccaProdotto(any());
        verify(repository, never()).annulla(any(), any());
    }

    @Test
    void scadutaConSoleOfferteRitirateAnnullaSenzaMovimenti() {
        var asta = asta(Stato.APERTA);
        when(repository.haOfferte(1)).thenReturn(true);
        when(repository.leader(1)).thenReturn(Optional.empty());
        servizio(asta.fineAt().plusSeconds(90)).annulla(1);
        verifyNoInteractions(portafogli);
        verify(repository).annulla(asta, asta.fineAt().plusSeconds(90));
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
        return new AnnullamentoAstaService(repository, portafogli, publisher, Clock.fixed(now, ZoneOffset.UTC));
    }
}
