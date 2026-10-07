package it.esercitazione.liveauction.producer.asta.repos;

import it.esercitazione.liveauction.producer.asta.models.Stato;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** Proiezioni di sola lettura: non richiedono entity del modulo offerte. */
@Repository
public class AstaLetturaRepository {

    private static final String CAMPI_ASTA = """
            a.id, a.stato, a.prezzo_iniziale, a.incremento_minimo, a.offerta_corrente,
            a.inizio_at, a.fine_at, a.sequence, p.id AS prodotto_id, p.nome AS prodotto_nome,
            (SELECT count(*) FROM offerte o WHERE o.asta_id = a.id) AS numero_offerte
            """;
    private static final String TABELLE_LOBBY = """
            FROM aste a JOIN prodotti p ON p.id = a.prodotto_id
            JOIN categorie c ON c.id = p.categoria_id WHERE TRUE
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public AstaLetturaRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public DatiPagina cerca(Stato stato, String categoria, String query, int page, int size) {
        StringBuilder filtro = new StringBuilder(TABELLE_LOBBY);
        MapSqlParameterSource parametri = new MapSqlParameterSource();
        if (stato != null) {
            filtro.append(" AND a.stato = :stato");
            parametri.addValue("stato", stato.name());
        }
        if (categoria != null && !categoria.isBlank()) {
            filtro.append(" AND c.slug = :categoria");
            parametri.addValue("categoria", categoria.trim());
        }
        if (query != null && !query.isBlank()) {
            filtro.append("""
                     AND (lower(p.nome) LIKE :testo ESCAPE '!'
                     OR lower(p.sku) LIKE :testo ESCAPE '!'
                     OR lower(p.descrizione) LIKE :testo ESCAPE '!')
                    """);
            String testo = query.trim().toLowerCase(Locale.ROOT)
                    .replace("!", "!!").replace("%", "!%").replace("_", "!_");
            parametri.addValue("testo", "%" + testo + "%");
        }
        long totale = jdbc.queryForObject("SELECT count(*) " + filtro, parametri, Long.class);
        parametri.addValue("size", size).addValue("offset", (long) page * size);
        List<DatiAsta> contenuto = jdbc.query("SELECT " + CAMPI_ASTA + filtro
                        + " ORDER BY a.inizio_at, a.id LIMIT :size OFFSET :offset",
                parametri, (rs, row) -> leggiAsta(rs));
        return new DatiPagina(contenuto, totale);
    }

    public Optional<DatiSnapshot> snapshot(long id) {
        // Una sola SELECT mantiene coerenti stato, sequence, conteggio e leader sotto concorrenza.
        String sql = "SELECT " + CAMPI_ASTA + """
                , leader.offerente_id, offerente.username AS offerente_username,
                a.vincitore_id, vincitore.username AS vincitore_username, a.chiusa_at
                FROM aste a JOIN prodotti p ON p.id = a.prodotto_id
                LEFT JOIN LATERAL (
                    SELECT o.offerente_id FROM offerte o WHERE o.asta_id = a.id
                    ORDER BY o.importo DESC, o.data_offerta DESC, o.id DESC LIMIT 1
                ) leader ON TRUE
                LEFT JOIN utenti offerente ON offerente.id = leader.offerente_id
                LEFT JOIN utenti vincitore ON vincitore.id = a.vincitore_id
                WHERE a.id = :id
                """;
        return jdbc.query(sql, new MapSqlParameterSource("id", id), (rs, row) -> new DatiSnapshot(
                leggiAsta(rs),
                rs.getObject("offerente_id", Long.class), rs.getString("offerente_username"),
                rs.getObject("vincitore_id", Long.class), rs.getString("vincitore_username"),
                istante(rs, "chiusa_at"))).stream().findFirst();
    }

    private static DatiAsta leggiAsta(ResultSet rs) throws SQLException {
        return new DatiAsta(rs.getLong("id"), Stato.valueOf(rs.getString("stato")),
                rs.getLong("prodotto_id"), rs.getString("prodotto_nome"),
                rs.getBigDecimal("prezzo_iniziale"), rs.getBigDecimal("incremento_minimo"),
                rs.getBigDecimal("offerta_corrente"), istante(rs, "inizio_at"), istante(rs, "fine_at"),
                rs.getLong("sequence"), rs.getLong("numero_offerte"));
    }

    private static Instant istante(ResultSet rs, String campo) throws SQLException {
        Timestamp valore = rs.getTimestamp(campo);
        return valore == null ? null : valore.toInstant();
    }

    public record DatiAsta(Long id, Stato stato, Long prodottoId, String prodottoNome,
                          BigDecimal prezzoIniziale, BigDecimal incrementoMinimo, BigDecimal offertaCorrente,
                          Instant inizioAt, Instant fineAt, long sequence, long numeroOfferte) {
    }

    public record DatiPagina(List<DatiAsta> content, long totale) {
    }

    public record DatiSnapshot(DatiAsta asta,
                              Long offerenteId, String offerenteUsername,
                              Long vincitoreId, String vincitoreUsername, Instant chiusaAt) {
    }
}
