package it.esercitazione.liveauction.producer.asta.services;

import it.esercitazione.liveauction.producer.asta.events.EventiOffertePublisher;
import it.esercitazione.liveauction.producer.asta.events.EventoOfferte;
import it.esercitazione.liveauction.producer.asta.exceptions.OffertaException;
import it.esercitazione.liveauction.producer.asta.repos.OfferteRepository;
import it.esercitazione.liveauction.producer.asta.repos.OfferteRepository.*;
import it.esercitazione.liveauction.producer.asta.requests.OffertaRequest;
import it.esercitazione.liveauction.producer.asta.responses.OffertaResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.Map;

@Service
@Validated
public class OffertaService {
    private final OfferteRepository offerte;
    private final EventiOffertePublisher eventi;
    private final Clock clock;

    public OffertaService(OfferteRepository offerte, EventiOffertePublisher eventi,
                          @Qualifier("clockOfferte") Clock clock) {
        this.offerte = offerte;
        this.eventi = eventi;
        this.clock = clock;
    }

    @Transactional
    @PreAuthorize("hasRole('USER') and authentication.token.subject == #p0.toString()")
    public OffertaResponse piazza(@Positive long utenteId, @Positive long astaId,
                                  @NotNull @Valid OffertaRequest request) {
        // Prima l'utente: impedisce nuove offerte durante l'eliminazione dell'account.
        offerte.bloccaUtente(utenteId).filter(utente -> utente.attivo() && "USER".equals(utente.ruolo()))
                .orElseThrow(() -> errore(HttpStatus.FORBIDDEN, "OPERAZIONE_NON_CONSENTITA",
                        "L'offerente deve essere un utente USER attivo"));
        AstaBloccata asta = offerte.bloccaAsta(astaId);
        OffertaRegistrata precedenteComando = offerte.trovaComando(request.clientBidId()).orElse(null);
        if (precedenteComando != null) {
            if (precedenteComando.astaId() != astaId || precedenteComando.utenteId() != utenteId
                    || precedenteComando.importo().compareTo(request.importo()) != 0) {
                throw errore(HttpStatus.CONFLICT, "CLIENT_BID_ID_GIA_UTILIZZATO",
                        "clientBidId già utilizzato con asta, offerente o importo diversi");
            }
            // Anche dopo la scadenza: il retry non esegue nuovamente il comando accettato.
            return new OffertaResponse(precedenteComando.id(), request.clientBidId(), precedenteComando.importo(),
                    true, precedenteComando.ritirataAt() != null, true, offerte.stato(astaId, clock.instant()));
        }
        verificaApertura(asta, clock.instant());
        if (request.knownSequence() > asta.sequence()) {
            throw errore(HttpStatus.CONFLICT, "SEQUENCE_NON_AGGIORNATA", "La sequenza indicata è futura");
        }
        OffertaRegistrata leader = offerte.leader(asta).orElse(null);
        if (leader != null && leader.utenteId() == utenteId) {
            throw errore(HttpStatus.CONFLICT, "RILANCIO_SU_SE_STESSO", "Il leader non può rilanciare su sé stesso");
        }
        BigDecimal minimo = leader == null ? asta.prezzoIniziale()
                : leader.importo().add(asta.incrementoMinimo());
        if (request.importo().compareTo(minimo) < 0) {
            throw errore(HttpStatus.CONFLICT, "OFFERTA_SUPERATA", "L'importo non raggiunge l'offerta minima");
        }
        var utenti = new HashSet<Long>();
        utenti.add(utenteId);
        if (leader != null) {
            utenti.add(leader.utenteId());
        }
        Map<Long, PortafoglioBloccato> portafogli = offerte.bloccaPortafogli(utenti);
        Instant now = clock.instant();
        // I lock possono aver richiesto tempo: la scadenza si verifica dopo l'attesa.
        verificaApertura(asta, now);
        if (portafogli.get(utenteId).disponibile().compareTo(request.importo()) < 0) {
            throw errore(HttpStatus.CONFLICT, "SALDO_INSUFFICIENTE", "Saldo disponibile insufficiente");
        }
        BigDecimal importo = request.importo().setScale(2);
        OffertaRegistrata accettata = offerte.inserisci(astaId, utenteId, request.clientBidId(), importo, now);
        offerte.movimenta(portafogli.get(utenteId), astaId, "RISERVA_OFFERTA", importo,
                BigDecimal.ZERO, importo, now);
        if (leader != null) {
            offerte.movimenta(portafogli.get(leader.utenteId()), astaId, "RILASCIO_OFFERTA", leader.importo(),
                    BigDecimal.ZERO, leader.importo().negate(), now);
        }
        offerte.cambiaLeader(asta, accettata, asta.fineAt().plusSeconds(20));
        var stato = offerte.stato(astaId, now);
        eventi.dopoCommit(new EventoOfferte("BID_ACCEPTED", stato, request.clientBidId(), importo, 20));
        return new OffertaResponse(accettata.id(), request.clientBidId(), importo, false, false,
                request.knownSequence() < asta.sequence(), stato);
    }

    private static void verificaApertura(AstaBloccata asta, Instant now) {
        if (!"APERTA".equals(asta.stato()) || now.isBefore(asta.inizioAt()) || !now.isBefore(asta.fineAt())) {
            throw errore(HttpStatus.CONFLICT, "ASTA_NON_APERTA", "L'asta non è aperta oppure è scaduta");
        }
    }

    private static OffertaException errore(HttpStatus status, String code, String dettaglio) {
        return new OffertaException(status, code, dettaglio);
    }
}
