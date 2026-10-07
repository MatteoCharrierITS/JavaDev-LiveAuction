package it.esercitazione.liveauction.producer.asta.services;

import it.esercitazione.liveauction.producer.asta.exceptions.AstaException;
import it.esercitazione.liveauction.producer.asta.models.Stato;
import it.esercitazione.liveauction.producer.asta.repos.AstaLetturaRepository;
import it.esercitazione.liveauction.producer.asta.repos.AstaLetturaRepository.DatiAsta;
import it.esercitazione.liveauction.producer.asta.repos.AstaLetturaRepository.DatiSnapshot;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class AstaLetturaServiceTests {

    private static final Instant INIZIO = Instant.parse("2026-10-03T16:30:00Z");
    private final AstaLetturaRepository repository = mock(AstaLetturaRepository.class);

    @ParameterizedTest
    @CsvSource({"-1,false", "0,true", "419,true", "420,false", "421,false"})
    void offerteConsentiteSoloDentroLaFinestraTemporale(long secondi, boolean consentite) {
        prepara(Stato.APERTA, null);
        var risposta = service(INIZIO.plusSeconds(secondi)).snapshot(42);
        assertThat(risposta.offerteConsentite()).isEqualTo(consentite);
    }

    @ParameterizedTest
    @EnumSource(value = Stato.class, names = {"PROGRAMMATA", "STANZA_APERTA", "CHIUSA", "ANNULLATA"})
    void gliAltriStatiNonConsentonoOfferte(Stato stato) {
        prepara(stato, null);
        assertThat(service(INIZIO).snapshot(42).offerteConsentite()).isFalse();
    }

    @ParameterizedTest
    @CsvSource({"giovanni,g***i", "ab,a***b", "a,***", "😀x😃,😀***😃"})
    void mascheraAncheUsernameBreviEUnicode(String username, String displayName) {
        prepara(Stato.APERTA, username);
        assertThat(service(INIZIO).snapshot(42).migliorOfferente().displayName()).isEqualTo(displayName);
    }

    @ParameterizedTest
    @CsvSource({"-1,12", "0,0", "0,-1", "0,101"})
    void rifiutaPaginazioneInvalidaPrimaDiInterrogareIlDatabase(int page, int size) {
        assertThatThrownBy(() -> service(INIZIO).cerca(null, null, null, page, size))
                .isInstanceOf(AstaException.class)
                .satisfies(e -> assertThat(((AstaException) e).getStatusCode().value()).isEqualTo(400));
        verifyNoInteractions(repository);
    }

    private void prepara(Stato stato, String username) {
        var asta = new DatiAsta(42L, stato, 3L, "Laptop", new BigDecimal("500.00"),
                new BigDecimal("1.00"), null, INIZIO, INIZIO.plusSeconds(420), 2, 0);
        when(repository.snapshot(42)).thenReturn(Optional.of(new DatiSnapshot(asta,
                username == null ? null : 9L, username, null, null, null)));
    }

    private AstaLetturaService service(Instant adesso) {
        return new AstaLetturaService(repository, Clock.fixed(adesso, ZoneOffset.UTC));
    }
}
