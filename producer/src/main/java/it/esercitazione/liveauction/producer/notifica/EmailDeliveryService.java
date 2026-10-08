package it.esercitazione.liveauction.producer.notifica;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.MailException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class EmailDeliveryService {
    private static final Logger LOG = LoggerFactory.getLogger(EmailDeliveryService.class);
    private final JdbcTemplate jdbc;
    private final EmailVittoriaSender sender;
    private final EmailProperties properties;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean inviaProssima() {
        if (!properties.isEnabled()) return false;
        var righe = jdbc.query("""
                SELECT n.asta_id, a.stato, a.chiusa_at, a.offerta_corrente, u.attivo, u.email, p.nome
                FROM notifiche_email n JOIN aste a ON a.id = n.asta_id
                JOIN utenti u ON u.id = a.vincitore_id JOIN prodotti p ON p.id = a.prodotto_id
                WHERE n.stato = 'PENDING' AND n.prossimo_tentativo_at <= CURRENT_TIMESTAMP
                ORDER BY n.prossimo_tentativo_at, n.asta_id
                LIMIT 1 FOR UPDATE OF n SKIP LOCKED
                """, (rs, row) -> new Destinatario(rs.getBoolean("attivo"), rs.getString("stato"),
                new EmailVittoriaSender.Vittoria(rs.getLong("asta_id"), rs.getString("email"),
                        rs.getString("nome"), rs.getBigDecimal("offerta_corrente"),
                        rs.getTimestamp("chiusa_at").toInstant())));
        if (righe.isEmpty()) return false;
        var riga = righe.getFirst();
        long id = riga.vittoria().astaId();
        if (!riga.attivo() || !"CHIUSA".equals(riga.stato())) {
            jdbc.update("UPDATE notifiche_email SET stato = 'SKIPPED', ultimo_errore = 'DESTINATARIO_NON_ATTIVO' WHERE asta_id = ?", id);
            return true;
        }
        try {
            // Il lock riguarda solo la coda, non asta/stock/saldi; timeout SMTP configurati.
            sender.invia(riga.vittoria());
            jdbc.update("""
                    UPDATE notifiche_email SET stato = 'SENT', tentativi = tentativi + 1,
                      inviata_at = CURRENT_TIMESTAMP, ultimo_errore = NULL WHERE asta_id = ?
                    """, id);
        } catch (MailException exception) {
            jdbc.update("""
                    UPDATE notifiche_email SET tentativi = tentativi + 1, ultimo_errore = 'SMTP_ERROR',
                      prossimo_tentativo_at = ? WHERE asta_id = ?
                    """, Timestamp.from(Instant.now().plusSeconds(properties.getRetrySeconds())), id);
            // Niente stack trace/provider message: possono contenere indirizzi o credenziali.
            LOG.warn("Invio email asta {} fallito; retry programmato", id);
        }
        return true;
    }

    private record Destinatario(boolean attivo, String stato, EmailVittoriaSender.Vittoria vittoria) {}
}
