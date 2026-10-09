package it.esercitazione.liveauction.producer.portafoglio.repos;

import it.esercitazione.liveauction.producer.portafoglio.exceptions.PortafoglioException;
import it.esercitazione.liveauction.producer.portafoglio.model.TipoMovimento;
import it.esercitazione.liveauction.producer.portafoglio.responses.MovimentoResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;

/** JDBC sullo schema Flyway; condiviso da API del portafoglio, rilanci e settlement. */
@Repository
@RequiredArgsConstructor
public class PortafoglioRepository {
    private static final BigDecimal MASSIMO_SALDO = new BigDecimal("9999999999.99");
    private final JdbcTemplate jdbc;

    public void verificaUtenteAttivo(long utenteId, boolean lock) {
        String sql = "SELECT id FROM utenti WHERE id = ? AND attivo AND ruolo = 'USER'"
                + (lock ? " FOR NO KEY UPDATE" : "");
        if (jdbc.query(sql, (rs, row) -> rs.getLong(1), utenteId).isEmpty()) {
            throw new PortafoglioException(HttpStatus.FORBIDDEN, "OPERAZIONE_NON_CONSENTITA",
                    "Il portafoglio personale richiede un utente USER attivo");
        }
    }

    public PortafoglioBloccato leggi(long utenteId) {
        return jdbc.query("SELECT id, utente_id, saldo_totale, saldo_riservato FROM portafogli WHERE utente_id = ?",
                (rs, row) -> new PortafoglioBloccato(rs.getLong("id"), rs.getLong("utente_id"),
                        rs.getBigDecimal("saldo_totale"), rs.getBigDecimal("saldo_riservato")), utenteId)
                .stream().findFirst().orElseThrow(() -> new PortafoglioException(HttpStatus.NOT_FOUND,
                        "RISORSA_NON_TROVATA", "Portafoglio non trovato"));
    }

    public long contaMovimenti(long portafoglioId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM movimenti_portafoglio WHERE portafoglio_id = ?",
                Long.class, portafoglioId);
    }

    public List<MovimentoResponse> movimenti(long portafoglioId, int page, int size) {
        return jdbc.query("""
                SELECT id, asta_id, tipo, importo, saldo_totale_dopo, saldo_riservato_dopo, data_movimento
                FROM movimenti_portafoglio WHERE portafoglio_id = ?
                ORDER BY data_movimento DESC, id DESC LIMIT ? OFFSET ?
                """, (rs, row) -> new MovimentoResponse(rs.getLong("id"), rs.getObject("asta_id", Long.class),
                TipoMovimento.valueOf(rs.getString("tipo")), rs.getBigDecimal("importo"),
                rs.getBigDecimal("saldo_totale_dopo"), rs.getBigDecimal("saldo_riservato_dopo"),
                rs.getTimestamp("data_movimento").toInstant()), portafoglioId, size, (long) page * size);
    }

    /** Un solo ordine globale: id del portafoglio, anche per ritiri su più aste. */
    @Transactional(propagation = Propagation.MANDATORY)
    public Map<Long, PortafoglioBloccato> bloccaPortafogli(Collection<Long> utenti) {
        if (utenti.isEmpty()) {
            return new HashMap<>();
        }
        List<Long> ids = utenti.stream().distinct().toList();
        String placeholders = String.join(",", Collections.nCopies(ids.size(), "?"));
        List<PortafoglioBloccato> portafogli = jdbc.query(
                "SELECT id, utente_id, saldo_totale, saldo_riservato FROM portafogli "
                        + "WHERE utente_id IN (" + placeholders + ") ORDER BY id FOR UPDATE",
                (rs, row) -> new PortafoglioBloccato(rs.getLong("id"), rs.getLong("utente_id"),
                        rs.getBigDecimal("saldo_totale"), rs.getBigDecimal("saldo_riservato")), ids.toArray());
        Map<Long, PortafoglioBloccato> risultato = new HashMap<>();
        portafogli.forEach(portafoglio -> risultato.put(portafoglio.utenteId(), portafoglio));
        if (risultato.size() != ids.size()) {
            throw new IllegalStateException("Portafoglio mancante per un utente coinvolto nell'asta");
        }
        return risultato;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public PortafoglioBloccato movimenta(PortafoglioBloccato portafoglio, Long astaId,
                                         TipoMovimento tipo, BigDecimal importo,
                                         BigDecimal variazioneTotale, BigDecimal variazioneRiserva, Instant now) {
        BigDecimal totale = portafoglio.totale().add(variazioneTotale);
        BigDecimal riservato = portafoglio.riservato().add(variazioneRiserva);
        if (totale.compareTo(MASSIMO_SALDO) > 0) {
            throw new PortafoglioException(HttpStatus.CONFLICT, "LIMITE_SALDO_SUPERATO",
                    "Il conto destinatario supera il saldo massimo consentito");
        }
        if (importo.signum() <= 0 || totale.signum() < 0 || riservato.signum() < 0
                || riservato.compareTo(totale) > 0) {
            throw new IllegalStateException("Saldo o riserva incoerenti con l'offerta");
        }
        jdbc.update("""
                UPDATE portafogli SET saldo_totale = ?, saldo_riservato = ?,
                    versione = versione + 1, data_modifica = ? WHERE id = ?
                """, totale, riservato, Timestamp.from(now), portafoglio.id());
        jdbc.update("""
                INSERT INTO movimenti_portafoglio
                    (portafoglio_id, asta_id, tipo, importo, saldo_totale_dopo, saldo_riservato_dopo, data_movimento)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, portafoglio.id(), astaId, tipo.name(), importo, totale, riservato, Timestamp.from(now));
        return new PortafoglioBloccato(portafoglio.id(), portafoglio.utenteId(), totale, riservato);
    }

    public record PortafoglioBloccato(long id, long utenteId, BigDecimal totale, BigDecimal riservato) {
        public BigDecimal disponibile() {
            return totale.subtract(riservato);
        }
    }
}
