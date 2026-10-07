package it.esercitazione.liveauction.producer.asta.services;

import it.esercitazione.liveauction.producer.asta.events.EventiOffertePublisher;
import it.esercitazione.liveauction.producer.asta.events.EventoOfferte;
import it.esercitazione.liveauction.producer.asta.repos.OfferteRepository;
import it.esercitazione.liveauction.producer.asta.repos.OfferteRepository.*;
import it.esercitazione.liveauction.producer.asta.responses.ChiusuraAstaResponse;
import jakarta.validation.constraints.Positive;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;

/** Operazione interna al Producer: il job del ciclo di vita la richiama alla scadenza. */
@Service
@Validated
public class ChiusuraAstaService {
    private final OfferteRepository offerte;
    private final EventiOffertePublisher eventi;
    private final Clock clock;

    public ChiusuraAstaService(OfferteRepository offerte, EventiOffertePublisher eventi,
                               @Qualifier("clockOfferte") Clock clock) {
        this.offerte = offerte;
        this.eventi = eventi;
        this.clock = clock;
    }

    @Transactional
    public ChiusuraAstaResponse chiudi(@Positive long astaId) {
        AstaBloccata asta = offerte.bloccaAsta(astaId);
        if (!"APERTA".equals(asta.stato()) || clock.instant().isBefore(asta.fineAt())) {
            return asta.esitoChiusura();
        }
        OffertaRegistrata leader = offerte.leader(asta).orElse(null);
        Long vincitoreId = leader == null ? null : leader.utenteId();
        Instant now;
        if (leader != null) {
            var portafogli = offerte.bloccaPortafogli(List.of(leader.utenteId(), asta.adminId()));
            now = clock.instant();
            if (leader.utenteId() == asta.adminId()) {
                throw new IllegalStateException("Il conto amministrativo non può essere il vincitore");
            }
            offerte.movimenta(portafogli.get(leader.utenteId()), astaId, "PAGAMENTO_ASTA", leader.importo(),
                    leader.importo().negate(), leader.importo().negate(), now);
            offerte.movimenta(portafogli.get(asta.adminId()), astaId, "INCASSO_ASTA", leader.importo(),
                    leader.importo(), BigDecimal.ZERO, now);
        } else {
            now = clock.instant();
        }
        offerte.trasferisciProdotto(asta, vincitoreId);
        offerte.concludi(asta, vincitoreId, now);
        var stato = offerte.stato(astaId, now);
        eventi.dopoCommit(new EventoOfferte("AUCTION_CLOSED", stato, null, asta.offertaCorrente(), 0));
        return new ChiusuraAstaResponse(astaId, true, "CHIUSA", vincitoreId,
                leader == null ? null : leader.importo(), now, stato.sequence());
    }
}
