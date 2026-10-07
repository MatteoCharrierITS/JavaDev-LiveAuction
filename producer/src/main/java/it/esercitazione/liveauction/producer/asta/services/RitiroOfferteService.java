package it.esercitazione.liveauction.producer.asta.services;

import it.esercitazione.liveauction.producer.asta.events.EventiOffertePublisher;
import it.esercitazione.liveauction.producer.asta.events.EventoOfferte;
import it.esercitazione.liveauction.producer.asta.repos.OfferteRepository;
import it.esercitazione.liveauction.producer.asta.repos.OfferteRepository.*;
import it.esercitazione.liveauction.producer.auth.events.EliminazioneUtenteRichiesta;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.*;

@Service
public class RitiroOfferteService {
    private final OfferteRepository offerte;
    private final EventiOffertePublisher eventi;
    private final Clock clock;

    public RitiroOfferteService(OfferteRepository offerte, EventiOffertePublisher eventi,
                                @Qualifier("clockOfferte") Clock clock) {
        this.offerte = offerte;
        this.eventi = eventi;
        this.clock = clock;
    }

    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    public void eliminaOfferte(EliminazioneUtenteRichiesta richiesta) {
        long utenteId = richiesta.utenteId();
        if (offerte.bloccaUtente(utenteId).isEmpty()) {
            return;
        }
        // L'utente è bloccato: nessuna sua nuova offerta può sfuggire a questa selezione.
        List<AstaBloccata> aste = offerte.asteConOfferteUtente(utenteId).stream()
                .map(offerte::bloccaAsta)
                .filter(asta -> !"CHIUSA".equals(asta.stato()) && !"ANNULLATA".equals(asta.stato()))
                .toList();
        Map<Long, List<OffertaRegistrata>> candidati = new HashMap<>();
        Set<Long> utenti = new HashSet<>();
        utenti.add(utenteId);
        for (AstaBloccata asta : aste) {
            List<OffertaRegistrata> precedenti = offerte.candidati(asta.id());
            candidati.put(asta.id(), precedenti);
            precedenti.forEach(offerta -> utenti.add(offerta.utenteId()));
            offerte.leader(asta).ifPresent(leader -> utenti.add(leader.utenteId()));
        }
        if (aste.isEmpty()) {
            return;
        }
        // Blocca TUTTI i wallet prima di modificarne uno: evita inversioni tra aste diverse.
        Map<Long, PortafoglioBloccato> portafogli = offerte.bloccaPortafogli(utenti);
        Instant now = clock.instant();
        for (AstaBloccata asta : aste) {
            OffertaRegistrata leader = offerte.leader(asta).orElse(null);
            offerte.ritira(asta.id(), utenteId, now);
            if (leader != null && leader.utenteId() == utenteId) {
                portafogli.put(utenteId, offerte.movimenta(portafogli.get(utenteId), asta.id(),
                        "RILASCIO_OFFERTA", leader.importo(), BigDecimal.ZERO, leader.importo().negate(), now));
                leader = null;
                for (OffertaRegistrata precedente : candidati.get(asta.id())) {
                    if (precedente.utenteId() == utenteId) {
                        continue;
                    }
                    PortafoglioBloccato portafoglio = portafogli.get(precedente.utenteId());
                    if (portafoglio.disponibile().compareTo(precedente.importo()) >= 0) {
                        portafogli.put(precedente.utenteId(), offerte.movimenta(portafoglio, asta.id(),
                                "RISERVA_OFFERTA", precedente.importo(), BigDecimal.ZERO, precedente.importo(), now));
                        leader = precedente;
                        break;
                    }
                }
            }
            // Le estensioni già concesse rimangono; nessuna nuova estensione per il ripristino.
            offerte.cambiaLeader(asta, leader, asta.fineAt());
            eventi.dopoCommit(new EventoOfferte("AUCTION_SNAPSHOT", offerte.stato(asta.id(), now), null, null, 0));
        }
    }
}
