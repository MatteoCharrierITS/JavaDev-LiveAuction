package it.esercitazione.liveauction.producer.asta.services;

import it.esercitazione.liveauction.producer.asta.exceptions.AstaException;
import it.esercitazione.liveauction.producer.asta.models.Asta;
import it.esercitazione.liveauction.producer.asta.models.Stato;
import it.esercitazione.liveauction.producer.asta.repos.AstaRepository;
import it.esercitazione.liveauction.producer.asta.repos.ProdottoAstaRepository;
import it.esercitazione.liveauction.producer.asta.requests.ProgrammaAstaRequest;
import it.esercitazione.liveauction.producer.asta.responses.ProgrammaAstaResponse;
import it.esercitazione.liveauction.producer.auth.models.Ruolo;
import it.esercitazione.liveauction.producer.auth.models.Utente;
import it.esercitazione.liveauction.producer.auth.repos.UtenteRepository;
import it.esercitazione.liveauction.producer.prodotto.models.Prodotto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AstaServiceTests {

    private final ProdottoAstaRepository prodottoRepository = mock(ProdottoAstaRepository.class);
    private final AstaRepository astaRepository = mock(AstaRepository.class);
    private final UtenteRepository utenteRepository = mock(UtenteRepository.class);
    private final AstaService service = new AstaService(prodottoRepository, astaRepository, utenteRepository,
            Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC));

    @Test
    void programmaAstaERiservaUnaUnita() {
        Prodotto prodotto = prodotto(true, 2);
        prodotto.setQuantitaBloccata(1);
        Utente admin = admin(true, Ruolo.ADMIN);
        when(utenteRepository.findById(9L)).thenReturn(Optional.of(admin));
        when(prodottoRepository.trovaPerProgrammazioneConLock(1L)).thenReturn(Optional.of(prodotto));
        when(astaRepository.saveAndFlush(any(Asta.class))).thenAnswer(invocation -> {
            Asta asta = invocation.getArgument(0);
            asta.setId(77L);
            return asta;
        });

        ProgrammaAstaResponse asta = service.programmaAsta(9L, richiesta(new BigDecimal("500")));

        assertThat(asta.id()).isEqualTo(77L);
        assertThat(asta.prodotto().id()).isEqualTo(prodotto.getId());
        assertThat(asta.stato()).isEqualTo(Stato.PROGRAMMATA);
        assertThat(asta.prezzoIniziale()).isEqualTo(new BigDecimal("500.00"));
        assertThat(asta.incrementoMinimo()).isEqualTo(new BigDecimal("1.00"));
        assertThat(asta.inizioAt()).isEqualTo(Instant.parse("2026-10-03T16:30:00Z"));
        assertThat(asta.aperturaStanzaAt()).isEqualTo(Instant.parse("2026-10-03T16:27:00Z"));
        assertThat(asta.fineAt()).isEqualTo(Instant.parse("2026-10-03T16:37:00Z"));
        assertThat(asta.serverTime()).isEqualTo(Instant.parse("2026-01-01T00:00:00Z"));
        assertThat(asta.sequence()).isZero();
        assertThat(prodotto.getQuantitaDisponibile()).isEqualTo(1);
        assertThat(prodotto.getQuantitaBloccata()).isEqualTo(2);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"0", "-1", "0.001", "10000000000", "1E+10", "1E+2147483647"})
    void rifiutaPrezziNonValidiPrimaDiBloccareLoStock(String valore) {
        BigDecimal prezzo = valore == null ? null : new BigDecimal(valore);
        assertThatThrownBy(() -> service.programmaAsta(9L, richiesta(prezzo)))
                .isInstanceOfSatisfying(AstaException.class, exception -> {
                    assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
                    assertThat(exception.getBody().getProperties())
                            .containsEntry("code", "PREZZO_INIZIALE_NON_VALIDO");
                });
        verifyNoInteractions(prodottoRepository, astaRepository, utenteRepository);
    }

    @ParameterizedTest
    @CsvSource({"false, ADMIN", "true, USER"})
    void rifiutaCreatoreNonAdminOInattivo(boolean attivo, Ruolo ruolo) {
        when(utenteRepository.findById(9L)).thenReturn(Optional.of(admin(attivo, ruolo)));
        assertAdminNonConsentito();
    }

    @Test
    void rifiutaCreatoreInesistente() {
        when(utenteRepository.findById(9L)).thenReturn(Optional.empty());
        assertAdminNonConsentito();
    }

    @Test
    void rifiutaOrarioScadutoDuranteAttesaDelLock() {
        Clock clock = mock(Clock.class);
        when(clock.instant()).thenReturn(Instant.parse("2026-10-03T16:29:00Z"),
                Instant.parse("2026-10-03T16:30:00Z"));
        AstaService servizio = new AstaService(prodottoRepository, astaRepository, utenteRepository, clock);
        Prodotto prodotto = prodotto(true, 1);
        when(utenteRepository.findById(9L)).thenReturn(Optional.of(admin(true, Ruolo.ADMIN)));
        when(prodottoRepository.trovaPerProgrammazioneConLock(1L)).thenReturn(Optional.of(prodotto));

        assertThatThrownBy(() -> servizio.programmaAsta(9L, richiesta(new BigDecimal("500.00"))))
                .isInstanceOfSatisfying(AstaException.class, exception ->
                        assertThat(exception.getBody().getProperties())
                                .containsEntry("code", "DATA_INIZIO_NON_VALIDA"));
        assertThat(prodotto.getQuantitaDisponibile()).isEqualTo(1);
        assertThat(prodotto.getQuantitaBloccata()).isZero();
        verifyNoInteractions(astaRepository);
    }

    private void assertAdminNonConsentito() {
        assertThatThrownBy(() -> service.programmaAsta(9L, richiesta(new BigDecimal("500.00"))))
                .isInstanceOfSatisfying(AstaException.class, exception -> {
                    assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(exception.getBody().getProperties())
                            .containsEntry("code", "OPERAZIONE_NON_CONSENTITA");
                });
        verifyNoInteractions(prodottoRepository, astaRepository);
    }

    private static Utente admin(boolean attivo, Ruolo ruolo) {
        Utente admin = new Utente();
        admin.setId(9L);
        admin.setAttivo(attivo);
        admin.setRuolo(ruolo);
        return admin;
    }

    private static ProgrammaAstaRequest richiesta(BigDecimal prezzo) {
        return new ProgrammaAstaRequest(1L, LocalDateTime.parse("2026-10-03T18:30:00"),
                "Europe/Rome", prezzo);
    }

    @Test
    void restituisceProdottoAstabileSenzaModificareLoStock() {
        Prodotto prodotto = prodotto(true, 1);
        prodotto.setQuantitaBloccata(2);
        when(prodottoRepository.trovaPerProgrammazioneConLock(1L)).thenReturn(Optional.of(prodotto));

        assertThat(service.bloccaProdottoPerProgrammazione(1L)).isSameAs(prodotto);
        assertThat(prodotto.getQuantitaDisponibile()).isEqualTo(1);
        assertThat(prodotto.getQuantitaBloccata()).isEqualTo(2);
    }

    @Test
    void rifiutaProdottoInesistente() {
        when(prodottoRepository.trovaPerProgrammazioneConLock(1L)).thenReturn(Optional.empty());
        assertErroreProdotto(HttpStatus.NOT_FOUND, "RISORSA_NON_TROVATA");
    }

    @Test
    void rifiutaProdottoNonAstabile() {
        when(prodottoRepository.trovaPerProgrammazioneConLock(1L))
                .thenReturn(Optional.of(prodotto(false, 1)));
        assertErroreProdotto(HttpStatus.UNPROCESSABLE_ENTITY, "PRODOTTO_NON_ASTABILE");
    }

    @Test
    void rifiutaStockEsaurito() {
        when(prodottoRepository.trovaPerProgrammazioneConLock(1L))
                .thenReturn(Optional.of(prodotto(true, 0)));
        assertErroreProdotto(HttpStatus.CONFLICT, "PRODOTTO_NON_DISPONIBILE");
    }

    private static Prodotto prodotto(boolean astabile, int disponibile) {
        Prodotto prodotto = new Prodotto();
        prodotto.setId(1L);
        prodotto.setAstabile(astabile);
        prodotto.setQuantitaDisponibile(disponibile);
        return prodotto;
    }

    private void assertErroreProdotto(HttpStatus status, String codice) {
        assertThatThrownBy(() -> service.bloccaProdottoPerProgrammazione(1L))
                .isInstanceOfSatisfying(AstaException.class, exception -> {
                    assertThat(exception.getStatusCode()).isEqualTo(status);
                    assertThat(exception.getBody().getProperties()).containsEntry("code", codice);
                });
    }

    @ParameterizedTest
    @CsvSource({
            "2026-01-15T18:30:00, 2026-01-15T17:30:00Z",
            "2026-10-03T18:30:00, 2026-10-03T16:30:00Z"
    })
    void converteOraSolareELegale(String locale, String utc) {
        assertThat(service.convertiInizio(LocalDateTime.parse(locale), "Europe/Rome"))
                .isEqualTo(Instant.parse(utc));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "2026-03-29T02:30:00", // Ora inesistente nel passaggio all'ora legale.
            "2026-10-25T02:30:00", // Ora ambigua nel ritorno all'ora solare.
            "2025-12-31T18:30:00", // Data passata.
            "2026-01-01T01:00:00"  // Esattamente il tempo corrente del server.
    })
    void rifiutaDateNonValide(String locale) {
        assertErroreData(LocalDateTime.parse(locale), "Europe/Rome");
    }

    @Test
    void rifiutaDataMancante() {
        assertErroreData(null, "Europe/Rome");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"UTC", "Europe/Paris", " Europe/Rome "})
    void rifiutaFusiDiversiDaRoma(String zona) {
        assertErroreData(LocalDateTime.parse("2026-10-03T18:30:00"), zona);
    }

    private void assertErroreData(LocalDateTime locale, String zona) {
        assertThatThrownBy(() -> service.convertiInizio(locale, zona))
                .isInstanceOfSatisfying(AstaException.class, exception -> {
                    assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
                    assertThat(exception.getBody().getProperties())
                            .containsEntry("code", "DATA_INIZIO_NON_VALIDA");
                });
    }
}
