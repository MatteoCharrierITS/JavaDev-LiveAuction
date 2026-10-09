package it.esercitazione.liveauction.producer.asta.repos;

import it.esercitazione.liveauction.producer.portafoglio.repos.PortafoglioRepository;
import it.esercitazione.liveauction.producer.portafoglio.repos.PortafoglioRepository.PortafoglioBloccato;
import it.esercitazione.liveauction.producer.portafoglio.model.TipoMovimento;
import it.esercitazione.liveauction.producer.portafoglio.exceptions.PortafoglioException;
import it.esercitazione.liveauction.producer.asta.exceptions.OffertaException;
import it.esercitazione.liveauction.producer.asta.responses.ChiusuraAstaResponse;
import it.esercitazione.liveauction.producer.asta.responses.StatoOfferteResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;

/** Accesso transazionale per offerte/settlement, senza dipendere dal modello di programmazione. */
@Repository
@RequiredArgsConstructor
public class OfferteRepository {
    private final JdbcTemplate jdbc;
    private final PortafoglioRepository portafogli;

    public Optional<UtenteOfferta> bloccaUtente(long id) {
        // Compatibile con KEY SHARE delle FK: il settlement può assegnare l'inventario
        // mentre un'eliminazione attende il lock dell'asta, senza creare un ciclo.
        return jdbc.query("SELECT id, username, ruolo, attivo FROM utenti WHERE id = ? FOR NO KEY UPDATE",
                (rs, row) -> new UtenteOfferta(rs.getLong("id"), rs.getString("username"),
                        rs.getString("ruolo"), rs.getBoolean("attivo")), id).stream().findFirst();
    }

    public AstaBloccata bloccaAsta(long id) {
        return jdbc.query("SELECT * FROM aste WHERE id = ? FOR UPDATE",
                        (rs, row) -> asta(rs), id).stream().findFirst()
                .orElseThrow(() -> new OffertaException(HttpStatus.NOT_FOUND,
                        "RISORSA_NON_TROVATA", "Asta non trovata"));
    }

    public List<Long> asteConOfferteUtente(long utenteId) {
        return jdbc.query("""
                SELECT DISTINCT a.id FROM aste a JOIN offerte o ON o.asta_id = a.id
                WHERE o.offerente_id = ? AND o.ritirata_at IS NULL
                  AND a.stato IN ('PROGRAMMATA', 'STANZA_APERTA', 'APERTA')
                ORDER BY a.id
                """, (rs, row) -> rs.getLong(1), utenteId);
    }

    public Optional<OffertaRegistrata> trovaComando(UUID clientBidId) {
        return jdbc.query("SELECT * FROM offerte WHERE client_bid_id = ?",
                (rs, row) -> offerta(rs), clientBidId).stream().findFirst();
    }

    public Optional<OffertaRegistrata> leader(AstaBloccata asta) {
        Optional<OffertaRegistrata> risultato = jdbc.query("SELECT * FROM offerte WHERE asta_id = ? AND leader",
                (rs, row) -> offerta(rs), asta.id()).stream().findFirst();
        if (risultato.isEmpty() != (asta.offertaCorrente() == null)
                || (risultato.isPresent() && risultato.get().importo().compareTo(asta.offertaCorrente()) != 0)) {
            throw new IllegalStateException("Leader e prezzo corrente dell'asta non sono coerenti");
        }
        return risultato;
    }

    public List<OffertaRegistrata> candidati(long astaId) {
        return jdbc.query("""
                SELECT o.* FROM offerte o JOIN utenti u ON u.id = o.offerente_id
                WHERE o.asta_id = ? AND o.ritirata_at IS NULL AND u.attivo AND u.ruolo = 'USER'
                ORDER BY o.importo DESC, o.id DESC
                """, (rs, row) -> offerta(rs), astaId);
    }

    /** Delega al modulo economico mantenendo l'ordine dei lock usato dalle aste. */
    public Map<Long, PortafoglioBloccato> bloccaPortafogli(Collection<Long> utenti) {
        return portafogli.bloccaPortafogli(utenti);
    }

    public OffertaRegistrata inserisci(long astaId, long utenteId, UUID clientBidId,
                                       BigDecimal importo, Instant now) {
        List<Long> ids = jdbc.query("""
                INSERT INTO offerte (asta_id, offerente_id, client_bid_id, importo, data_offerta)
                VALUES (?, ?, ?, ?, ?) ON CONFLICT (client_bid_id) DO NOTHING RETURNING id
                """, (rs, row) -> rs.getLong(1), astaId, utenteId, clientBidId, importo, Timestamp.from(now));
        if (ids.isEmpty()) {
            // Copre anche lo stesso UUID inviato contemporaneamente a due aste diverse.
            throw new OffertaException(HttpStatus.CONFLICT, "CLIENT_BID_ID_GIA_UTILIZZATO",
                    "clientBidId già utilizzato per un'altra offerta");
        }
        return new OffertaRegistrata(ids.getFirst(), astaId, utenteId, clientBidId, importo, null);
    }

    public PortafoglioBloccato movimenta(PortafoglioBloccato portafoglio, long astaId,
                                         String tipo, BigDecimal importo,
                                         BigDecimal variazioneTotale, BigDecimal variazioneRiserva, Instant now) {
        try {
            return portafogli.movimenta(portafoglio, astaId, TipoMovimento.valueOf(tipo), importo,
                    variazioneTotale, variazioneRiserva, now);
        } catch (PortafoglioException exception) {
            // Conserva il contratto degli errori del comando STOMP esistente.
            throw new OffertaException(HttpStatus.valueOf(exception.getStatusCode().value()),
                    (String) exception.getBody().getProperties().get("code"), exception.getBody().getDetail());
        }
    }

    public void cambiaLeader(AstaBloccata asta, OffertaRegistrata leader, Instant fineAt) {
        jdbc.update("UPDATE offerte SET leader = FALSE WHERE asta_id = ? AND leader", asta.id());
        if (leader != null) {
            jdbc.update("UPDATE offerte SET leader = TRUE WHERE id = ? AND ritirata_at IS NULL", leader.id());
        }
        jdbc.update("""
                UPDATE aste SET offerta_corrente = ?, fine_at = ?,
                    sequence = sequence + 1, versione = versione + 1 WHERE id = ?
                """, leader == null ? null : leader.importo(), Timestamp.from(fineAt), asta.id());
    }

    public int ritira(long astaId, long utenteId, Instant now) {
        return jdbc.update("""
                UPDATE offerte SET leader = FALSE, ritirata_at = ?
                WHERE asta_id = ? AND offerente_id = ? AND ritirata_at IS NULL
                """, Timestamp.from(now), astaId, utenteId);
    }

    public void trasferisciProdotto(AstaBloccata asta, Long vincitoreId) {
        Integer bloccata = jdbc.query("SELECT quantita_bloccata FROM prodotti WHERE id = ? FOR UPDATE",
                (rs, row) -> rs.getInt(1), asta.prodottoId()).stream().findFirst().orElse(null);
        if (bloccata == null || bloccata < 1) {
            throw new IllegalStateException("L'asta non possiede un'unità di prodotto bloccata");
        }
        jdbc.update("""
                UPDATE prodotti SET quantita_bloccata = quantita_bloccata - 1,
                    quantita_disponibile = quantita_disponibile + ?, versione = versione + 1 WHERE id = ?
                """, vincitoreId == null ? 1 : 0, asta.prodottoId());
        if (vincitoreId != null) {
            jdbc.update("""
                    INSERT INTO inventario_utenti (utente_id, prodotto_id, quantita)
                    VALUES (?, ?, 1) ON CONFLICT (utente_id, prodotto_id)
                    DO UPDATE SET quantita = inventario_utenti.quantita + 1,
                        versione = inventario_utenti.versione + 1
                    """, vincitoreId, asta.prodottoId());
        }
    }

    public void concludi(AstaBloccata asta, Long vincitoreId, Instant now) {
        jdbc.update("""
                UPDATE aste SET stato = 'CHIUSA', vincitore_id = ?, chiusa_at = ?,
                    sequence = sequence + 1, versione = versione + 1 WHERE id = ?
                """, vincitoreId, Timestamp.from(now), asta.id());
    }

    public StatoOfferteResponse stato(long astaId, Instant now) {
        return jdbc.queryForObject("""
                SELECT a.*, o.offerente_id, u.username,
                    (SELECT COUNT(*) FROM offerte storico WHERE storico.asta_id = a.id) AS numero_offerte
                FROM aste a LEFT JOIN offerte o ON o.asta_id = a.id AND o.leader
                LEFT JOIN utenti u ON u.id = o.offerente_id WHERE a.id = ?
                """, (rs, row) -> new StatoOfferteResponse(rs.getLong("id"), rs.getLong("prodotto_id"),
                rs.getString("stato"), rs.getBigDecimal("offerta_corrente"),
                rs.getObject("offerente_id", Long.class), maschera(rs.getString("username")),
                rs.getLong("numero_offerte"), rs.getTimestamp("fine_at").toInstant(),
                rs.getObject("vincitore_id", Long.class), rs.getLong("sequence"), now), astaId);
    }

    private static String maschera(String username) {
        if (username == null) {
            return null;
        }
        return username.substring(0, 1) + "***" + (username.length() > 1
                ? username.substring(username.length() - 1) : "");
    }

    private static AstaBloccata asta(ResultSet rs) throws SQLException {
        Timestamp chiusaAt = rs.getTimestamp("chiusa_at");
        return new AstaBloccata(rs.getLong("id"), rs.getLong("prodotto_id"), rs.getLong("admin_id"),
                rs.getString("stato"), rs.getBigDecimal("prezzo_iniziale"), rs.getBigDecimal("incremento_minimo"),
                rs.getBigDecimal("offerta_corrente"), rs.getTimestamp("inizio_at").toInstant(),
                rs.getTimestamp("fine_at").toInstant(), rs.getObject("vincitore_id", Long.class),
                chiusaAt == null ? null : chiusaAt.toInstant(), rs.getLong("sequence"));
    }

    private static OffertaRegistrata offerta(ResultSet rs) throws SQLException {
        Timestamp ritirataAt = rs.getTimestamp("ritirata_at");
        return new OffertaRegistrata(rs.getLong("id"), rs.getLong("asta_id"), rs.getLong("offerente_id"),
                rs.getObject("client_bid_id", UUID.class), rs.getBigDecimal("importo"),
                ritirataAt == null ? null : ritirataAt.toInstant());
    }

    public record UtenteOfferta(long id, String username, String ruolo, boolean attivo) {}

    /** Proiezione JDBC privata al flusso offerte, non entity condivisa con la programmazione. */
    public record AstaBloccata(long id, long prodottoId, long adminId, String stato,
                              BigDecimal prezzoIniziale, BigDecimal incrementoMinimo, BigDecimal offertaCorrente,
                              Instant inizioAt, Instant fineAt, Long vincitoreId, Instant chiusaAt, long sequence) {
        public ChiusuraAstaResponse esitoChiusura() {
            return new ChiusuraAstaResponse(id, "CHIUSA".equals(stato), stato, vincitoreId,
                    vincitoreId == null ? null : offertaCorrente, chiusaAt, sequence);
        }
    }

    public record OffertaRegistrata(long id, long astaId, long utenteId, UUID clientBidId,
                                    BigDecimal importo, Instant ritirataAt) {}


}
