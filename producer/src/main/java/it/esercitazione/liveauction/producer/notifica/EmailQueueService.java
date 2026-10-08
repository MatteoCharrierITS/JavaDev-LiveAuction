package it.esercitazione.liveauction.producer.notifica;

import it.esercitazione.liveauction.producer.asta.events.EventoOfferte;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class EmailQueueService {
    private final JdbcTemplate jdbc;
    private final EmailProperties properties;

    // Evento già post-commit: la scrittura della coda deve aprire una nuova transazione.
    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void chiusura(EventoOfferte evento) {
        if ("AUCTION_CLOSED".equals(evento.type()) && evento.stato().vincitoreId() != null) {
            jdbc.update("""
                    INSERT INTO notifiche_email(asta_id)
                    SELECT id FROM aste WHERE id = ? AND stato = 'CHIUSA' AND vincitore_id IS NOT NULL
                      AND chiusa_at IS NOT NULL
                    ON CONFLICT (asta_id) DO NOTHING
                    """, evento.stato().auctionId());
        }
    }

    /** Recupera anche la finestra di crash fra commit del settlement e pubblicazione dell'evento. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int recupera() {
        return jdbc.update("""
                INSERT INTO notifiche_email(asta_id)
                SELECT a.id FROM aste a WHERE a.stato = 'CHIUSA' AND a.vincitore_id IS NOT NULL
                  AND a.chiusa_at IS NOT NULL
                  AND NOT EXISTS (SELECT 1 FROM notifiche_email n WHERE n.asta_id = a.id)
                ORDER BY a.id LIMIT ?
                ON CONFLICT (asta_id) DO NOTHING
                """, properties.getBatchSize());
    }
}
