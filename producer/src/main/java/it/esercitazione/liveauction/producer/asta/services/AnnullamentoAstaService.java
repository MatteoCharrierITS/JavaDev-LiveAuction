package it.esercitazione.liveauction.producer.asta.services;

import it.esercitazione.liveauction.producer.asta.events.AstaTransizioneEvent;
import it.esercitazione.liveauction.producer.asta.exceptions.AstaException;
import it.esercitazione.liveauction.producer.asta.models.Stato;
import it.esercitazione.liveauction.producer.asta.repos.AnnullamentoAstaRepository;
import it.esercitazione.liveauction.producer.asta.responses.AnnullamentoAstaResponse;
import jakarta.validation.constraints.Positive;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.validation.annotation.Validated;

import it.esercitazione.liveauction.producer.portafoglio.model.TipoMovimento;
import it.esercitazione.liveauction.producer.portafoglio.repos.PortafoglioRepository;
import it.esercitazione.liveauction.producer.portafoglio.repos.PortafoglioRepository.PortafoglioBloccato;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Service
@Validated
public class AnnullamentoAstaService {
    /**
     * Lo scheduler ritenta la chiusura ogni secondo: un'asta con offerte ancora APERTA
     * oltre questo margine dalla scadenza è davvero bloccata e l'ADMIN può annullarla.
     */
    public static final Duration GRAZIA_CHIUSURA = Duration.ofSeconds(60);
    private static final Logger LOG = LoggerFactory.getLogger(AnnullamentoAstaService.class);
    private final AnnullamentoAstaRepository repository;
    private final ApplicationEventPublisher publisher;
    private final PortafoglioRepository portafogli;
    private final Clock clock;

    public AnnullamentoAstaService(AnnullamentoAstaRepository repository, PortafoglioRepository portafogli,
                                  ApplicationEventPublisher publisher, @Qualifier("clockOfferte") Clock clock) {
        this.repository = repository;
        this.portafogli = portafogli;
        this.publisher = publisher;
        this.clock = clock;
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public AnnullamentoAstaResponse annulla(@Positive long astaId) {
        var asta = repository.blocca(astaId);
        if (asta.stato() == Stato.ANNULLATA) {
            return asta.esito();
        }
        if (asta.stato() == Stato.CHIUSA) {
            throw new AstaException(HttpStatus.CONFLICT, "ASTA_NON_ANNULLABILE", "L'asta è già conclusa");
        }
        // Anche le offerte ritirate sono offerte ricevute: non ampliare la regola concordata.
        boolean conOfferte = repository.haOfferte(astaId);
        // Con il lock dell'asta nessuna offerta può spostare fine_at: una scadenza osservata qui resta tale.
        boolean bloccataInChiusura = false;
        if (conOfferte) {
            Instant adesso = clock.instant();
            boolean scaduta = asta.stato() == Stato.APERTA && !adesso.isBefore(asta.fineAt());
            if (!scaduta) {
                throw new AstaException(HttpStatus.CONFLICT, "ASTA_CON_OFFERTE",
                        "Non è possibile annullare un'asta che ha ricevuto offerte");
            }
            if (adesso.isBefore(asta.fineAt().plus(GRAZIA_CHIUSURA))) {
                throw new AstaException(HttpStatus.CONFLICT, "CHIUSURA_IN_CORSO",
                        "L'asta è scaduta e la chiusura automatica è in corso: l'annullamento è consentito "
                                + "solo se l'asta risulta ancora aperta " + GRAZIA_CHIUSURA.toSeconds()
                                + " secondi dopo la scadenza");
            }
            bloccataInChiusura = true;
        }
        // Via d'uscita per un'asta APERTA scaduta con offerte che il settlement non riesce a chiudere
        // (es. LIMITE_SALDO_SUPERATO sul conto che incassa): la riserva del leader torna disponibile.
        // Senza offerte la chiusura restituisce già il prodotto, quindi resta la regola della scadenza.
        // Ordine dei lock come nel settlement: asta, portafogli, prodotto.
        var leader = bloccataInChiusura ? repository.leader(astaId).orElse(null) : null;
        PortafoglioBloccato portafoglio = leader == null ? null
                : portafogli.bloccaPortafogli(List.of(leader.utenteId())).get(leader.utenteId());
        repository.bloccaProdotto(asta);
        Instant now = clock.instant();
        if (!bloccataInChiusura && !now.isBefore(asta.fineAt())) {
            throw new AstaException(HttpStatus.CONFLICT, "ASTA_NON_ANNULLABILE", "L'asta è già scaduta");
        }
        if (leader != null) {
            portafogli.movimenta(portafoglio, astaId, TipoMovimento.RILASCIO_OFFERTA, leader.importo(),
                    BigDecimal.ZERO, leader.importo().negate(), now);
        }
        repository.annulla(asta, now);
        var evento = new AstaTransizioneEvent(AstaTransizioneEvent.Tipo.AUCTION_CANCELLED,
                astaId, asta.sequence() + 1, now, Stato.ANNULLATA, asta.inizioAt().minusSeconds(180),
                asta.inizioAt(), asta.fineAt());
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    publisher.publishEvent(evento);
                } catch (RuntimeException exception) {
                    LOG.error("Invio annullamento per asta {} fallito dopo il commit", astaId, exception);
                }
            }
        });
        return new AnnullamentoAstaResponse(astaId, Stato.ANNULLATA, now, evento.sequence());
    }
}
