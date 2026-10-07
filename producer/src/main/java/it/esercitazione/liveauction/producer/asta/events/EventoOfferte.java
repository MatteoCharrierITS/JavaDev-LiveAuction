package it.esercitazione.liveauction.producer.asta.events;

import it.esercitazione.liveauction.producer.asta.responses.StatoOfferteResponse;

import java.math.BigDecimal;
import java.util.UUID;

/** Evento interno post-commit: il modulo WebSocket/notifiche ne adatta il trasporto. */
public record EventoOfferte(
        String type, StatoOfferteResponse stato, UUID clientBidId,
        BigDecimal importo, int extensionSeconds
) {}
