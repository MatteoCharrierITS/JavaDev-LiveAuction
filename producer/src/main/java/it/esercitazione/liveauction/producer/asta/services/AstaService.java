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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

@Service
public class AstaService {

    private static final ZoneId ZONA_ASTE = ZoneId.of("Europe/Rome");

    private final ProdottoAstaRepository prodottoAstaRepository;
    private final AstaRepository astaRepository;
    private final UtenteRepository utenteRepository;
    private final Clock clock;

    @Autowired
    public AstaService(ProdottoAstaRepository prodottoAstaRepository,
                       AstaRepository astaRepository, UtenteRepository utenteRepository) {
        this(prodottoAstaRepository, astaRepository, utenteRepository, Clock.systemUTC());
    }

    AstaService(ProdottoAstaRepository prodottoAstaRepository, AstaRepository astaRepository,
                UtenteRepository utenteRepository, Clock clock) {
        this.prodottoAstaRepository = prodottoAstaRepository;
        this.astaRepository = astaRepository;
        this.utenteRepository = utenteRepository;
        this.clock = clock;
    }

    /** Riserva una unità e crea l'asta: entrambe le modifiche appartengono alla stessa transazione. */
    @Transactional
    @PreAuthorize("hasRole('ADMIN') and authentication.token.subject == #p0.toString()")
    public ProgrammaAstaResponse programmaAsta(long adminId, ProgrammaAstaRequest request) {
        Instant inizioAt = convertiInizio(request.inizioLocale(), request.timeZone());
        BigDecimal prezzoIniziale = validaPrezzoIniziale(request.prezzoIniziale());
        if (request.prodottoId() == null || request.prodottoId() <= 0) {
            throw AstaException.prodottoNonTrovato();
        }

        Utente admin = utenteRepository.findById(adminId)
                .filter(utente -> utente.isAttivo() && utente.getRuolo() == Ruolo.ADMIN)
                .orElseThrow(AstaException::adminNonConsentito);
        Prodotto prodotto = bloccaProdottoPerProgrammazione(request.prodottoId());

        // L'attesa del lock potrebbe aver superato l'orario richiesto.
        validaInizioFuturo(inizioAt);
        prodotto.setQuantitaDisponibile(prodotto.getQuantitaDisponibile() - 1);
        prodotto.setQuantitaBloccata(prodotto.getQuantitaBloccata() + 1);

        Asta asta = new Asta();
        asta.setProdotto(prodotto);
        asta.setAdmin(admin);
        asta.setStato(Stato.PROGRAMMATA);
        asta.setPrezzoIniziale(prezzoIniziale);
        asta.setInizioAt(inizioAt);
        asta.setFineAt(inizioAt.plusSeconds(7 * 60));

        // Il prodotto è gestito da JPA: il flush salva anche le sue quantità aggiornate.
        return ProgrammaAstaResponse.from(astaRepository.saveAndFlush(asta), clock.instant());
    }

    /** Il lock deve durare fino al salvataggio dell'asta nella transazione chiamante. */
    @Transactional(propagation = Propagation.MANDATORY)
    @PreAuthorize("hasRole('ADMIN')")
    public Prodotto bloccaProdottoPerProgrammazione(long prodottoId) {
        Prodotto prodotto = prodottoAstaRepository.trovaPerProgrammazioneConLock(prodottoId)
                .orElseThrow(() -> AstaException.prodottoNonTrovato());

        if (!prodotto.isAstabile()) {
            throw AstaException.prodottoNonAstabile();
        }
        if (prodotto.getQuantitaDisponibile() <= 0) {
            throw AstaException.prodottoNonDisponibile();
        }
        return prodotto;
    }

    /** Valida l'orario di programmazione e lo converte in un istante UTC. */
    @PreAuthorize("hasRole('ADMIN')")
    public Instant convertiInizio(LocalDateTime inizioLocale, String timeZone) {
        if (inizioLocale == null) {
            throw AstaException.dataInizioNonValida("Data e ora di inizio obbligatorie");
        }
        if (!ZONA_ASTE.getId().equals(timeZone)) {
            throw AstaException.dataInizioNonValida("Il fuso orario deve essere Europe/Rome");
        }

        List<ZoneOffset> offsetValidi = ZONA_ASTE.getRules().getValidOffsets(inizioLocale);
        if (offsetValidi.isEmpty()) {
            throw AstaException.dataInizioNonValida(
                    "L'orario di inizio non esiste in Europe/Rome per il cambio d'ora");
        }
        if (offsetValidi.size() > 1) {
            throw AstaException.dataInizioNonValida(
                    "L'orario di inizio è ambiguo in Europe/Rome per il cambio d'ora");
        }

        Instant inizioAt = inizioLocale.toInstant(offsetValidi.get(0));
        validaInizioFuturo(inizioAt);
        return inizioAt;
    }

    private void validaInizioFuturo(Instant inizioAt) {
        if (!inizioAt.isAfter(clock.instant())) {
            throw AstaException.dataInizioNonValida("La data di inizio deve essere futura");
        }
    }

    private static BigDecimal validaPrezzoIniziale(BigDecimal prezzo) {
        if (prezzo == null || prezzo.signum() <= 0 || prezzo.scale() > 2
                || (long) prezzo.precision() - prezzo.scale() > 10) {
            throw AstaException.prezzoInizialeNonValido();
        }
        return prezzo.setScale(2);
    }
}
