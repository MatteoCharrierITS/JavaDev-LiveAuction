package it.esercitazione.liveauction.producer.asta.repos;

import it.esercitazione.liveauction.producer.asta.models.Stato;
import it.esercitazione.liveauction.producer.asta.responses.AstaSintesiResponse.ProdottoSintesi;
import it.esercitazione.liveauction.producer.asta.responses.StoricoAstaResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class StoricoAstaRepository {
    private static final String CONCLUSA = "COALESCE(a.annullata_at, a.chiusa_at, a.data_creazione)";
    private final NamedParameterJdbcTemplate jdbc;

    public DatiPagina cerca(boolean soloVittorie, Long prodottoId, Long vincitoreId, Stato stato,
                            Instant da, Instant a, int page, int size) {
        StringBuilder filtro = new StringBuilder("""
                FROM aste a JOIN prodotti p ON p.id = a.prodotto_id
                WHERE a.stato IN ('CHIUSA', 'ANNULLATA')
                """);
        var parametri = new MapSqlParameterSource();
        if (soloVittorie) {
            filtro.append(" AND a.stato = 'CHIUSA' AND a.vincitore_id IS NOT NULL");
        }
        if (prodottoId != null) {
            filtro.append(" AND a.prodotto_id = :prodotto");
            parametri.addValue("prodotto", prodottoId);
        }
        if (vincitoreId != null) {
            filtro.append(" AND a.vincitore_id = :vincitore");
            parametri.addValue("vincitore", vincitoreId);
        }
        if (stato != null) {
            filtro.append(" AND a.stato = :stato");
            parametri.addValue("stato", stato.name());
        }
        if (da != null) {
            filtro.append(" AND " + CONCLUSA + " >= :da");
            parametri.addValue("da", Timestamp.from(da));
        }
        if (a != null) {
            filtro.append(" AND " + CONCLUSA + " <= :a");
            parametri.addValue("a", Timestamp.from(a));
        }
        long totale = jdbc.queryForObject("SELECT count(*) " + filtro, parametri, Long.class);
        parametri.addValue("size", size).addValue("offset", (long) page * size);
        var contenuto = jdbc.query("""
                SELECT a.id, a.stato, p.id AS prodotto_id, p.nome, p.immagine_url, a.vincitore_id,
                    CASE WHEN a.stato = 'CHIUSA' AND a.vincitore_id IS NOT NULL
                        THEN a.offerta_corrente END AS prezzo_finale, a.inizio_at, a.fine_at,
                """ + CONCLUSA + " AS conclusa_at " + filtro
                + " ORDER BY " + CONCLUSA + " DESC, a.id DESC LIMIT :size OFFSET :offset",
                parametri, (rs, row) -> new StoricoAstaResponse(rs.getLong("id"),
                        Stato.valueOf(rs.getString("stato")),
                        new ProdottoSintesi(rs.getLong("prodotto_id"), rs.getString("nome"),
                                rs.getString("immagine_url")),
                        rs.getObject("vincitore_id", Long.class), rs.getBigDecimal("prezzo_finale"),
                        rs.getTimestamp("inizio_at").toInstant(), rs.getTimestamp("fine_at").toInstant(),
                        rs.getTimestamp("conclusa_at").toInstant()));
        return new DatiPagina(contenuto, totale);
    }

    public record DatiPagina(List<StoricoAstaResponse> content, long totale) {}
}
