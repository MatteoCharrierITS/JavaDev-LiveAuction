package it.esercitazione.liveauction.producer.websocket;

import it.esercitazione.liveauction.producer.asta.events.AstaTransizioneEvent;
import it.esercitazione.liveauction.producer.asta.events.EventoOfferte;
import it.esercitazione.liveauction.producer.asta.responses.StatoOfferteResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;

/** I publisher di dominio emettono già dopo il commit: non usare AFTER_COMMIT qui. */
@Component
@RequiredArgsConstructor
public class AstaEventRelay {
    private final SimpMessagingTemplate messaging;

    @EventListener
    public void transizione(AstaTransizioneEvent evento) {
        messaging.convertAndSend("/topic/aste/" + evento.auctionId(), evento);
    }

    @EventListener
    public void offerta(EventoOfferte evento) {
        messaging.convertAndSend("/topic/aste/" + evento.stato().auctionId(), statoPubblico(evento));
    }

    static StatoPubblico statoPubblico(EventoOfferte evento) {
        StatoOfferteResponse s = evento.stato();
        // Non serializzare la proiezione interna: contiene gli ID degli utenti.
        return new StatoPubblico(evento.type(), s.auctionId(), s.sequence(), s.serverTime(),
                s.stato(), s.prodottoId(), evento.importo(), s.offertaCorrente(),
                s.offerenteDisplay(), s.numeroOfferte(), s.fineAt(), evento.extensionSeconds(),
                s.vincitoreId() == null ? null : s.offerenteDisplay());
    }

    public record StatoPubblico(String type, long auctionId, long sequence, Instant serverTime,
                               String stato, long prodottoId, BigDecimal importo,
                               BigDecimal offertaCorrente, String offerenteDisplay, long numeroOfferte,
                               Instant fineAt, int extensionSeconds, String vincitoreDisplay) {}
}
