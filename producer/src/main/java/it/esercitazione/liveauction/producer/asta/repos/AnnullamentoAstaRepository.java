package it.esercitazione.liveauction.producer.asta.repos;

import it.esercitazione.liveauction.producer.asta.exceptions.AstaException;
import it.esercitazione.liveauction.producer.asta.models.Stato;
import it.esercitazione.liveauction.producer.asta.responses.AnnullamentoAstaResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;

@Repository
@RequiredArgsConstructor
public class AnnullamentoAstaRepository {
    private final JdbcTemplate jdbc;

    public AstaDaAnnullare blocca(long id) {
        return jdbc.query("SELECT * FROM aste WHERE id = ? FOR UPDATE", (rs, row) -> {
            Timestamp annullata = rs.getTimestamp("annullata_at");
            return new AstaDaAnnullare(rs.getLong("id"), rs.getLong("prodotto_id"),
                    Stato.valueOf(rs.getString("stato")), rs.getTimestamp("inizio_at").toInstant(),
                    rs.getTimestamp("fine_at").toInstant(), rs.getLong("sequence"),
                    annullata == null ? null : annullata.toInstant());
        }, id).stream().findFirst().orElseThrow(AstaException::astaNonTrovata);
    }

    /** Il lock asta serializza questa verifica con offerte, ritiro e settlement. */
    public boolean haOfferte(long id) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM offerte WHERE asta_id = ?)", Boolean.class, id));
    }

    public void bloccaProdotto(AstaDaAnnullare asta) {
        // Stesso ordine di settlement: asta, poi prodotto; mai bloccare utenti/portafogli qui.
        Integer bloccata = jdbc.query("SELECT quantita_bloccata FROM prodotti WHERE id = ? FOR UPDATE",
                (rs, row) -> rs.getInt(1), asta.prodottoId()).stream().findFirst().orElse(null);
        if (bloccata == null || bloccata < 1) {
            throw new IllegalStateException("L'asta non possiede un'unità di prodotto bloccata");
        }
    }

    public void annulla(AstaDaAnnullare asta, Instant now) {
        jdbc.update("""
                UPDATE prodotti SET quantita_bloccata = quantita_bloccata - 1,
                    quantita_disponibile = quantita_disponibile + 1, versione = versione + 1 WHERE id = ?
                """, asta.prodottoId());
        jdbc.update("""
                UPDATE aste SET stato = 'ANNULLATA', annullata_at = ?,
                    sequence = sequence + 1, versione = versione + 1 WHERE id = ?
                """, Timestamp.from(now), asta.id());
    }

    public record AstaDaAnnullare(long id, long prodottoId, Stato stato, Instant inizioAt,
                                  Instant fineAt, long sequence, Instant annullataAt) {
        public AnnullamentoAstaResponse esito() {
            return new AnnullamentoAstaResponse(id, stato, annullataAt, sequence);
        }
    }
}
