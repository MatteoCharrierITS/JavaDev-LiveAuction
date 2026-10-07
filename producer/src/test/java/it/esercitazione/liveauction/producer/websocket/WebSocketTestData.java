package it.esercitazione.liveauction.producer.websocket;

import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.List;

/** Rimuove soltanto gli ID e gli username unici creati dal singolo test, anche dopo un fallimento. */
public class WebSocketTestData implements AutoCloseable {
    private final JdbcTemplate jdbc;
    public final List<String> usernames = new ArrayList<>();
    public final List<Long> userIds = new ArrayList<>();
    public Long categoryId;
    public Long productId;
    public Long auctionId;

    public WebSocketTestData(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void close() {
        if (auctionId != null) {
            jdbc.update("DELETE FROM movimenti_portafoglio WHERE asta_id = ?", auctionId);
            jdbc.update("DELETE FROM offerte WHERE asta_id = ?", auctionId);
            jdbc.update("DELETE FROM aste WHERE id = ?", auctionId);
        }
        if (productId != null) {
            jdbc.update("DELETE FROM inventario_utenti WHERE prodotto_id = ?", productId);
            jdbc.update("DELETE FROM prodotti WHERE id = ?", productId);
        }
        if (categoryId != null) {
            jdbc.update("DELETE FROM categorie WHERE id = ?", categoryId);
        }
        for (String username : usernames) {
            userIds.addAll(jdbc.queryForList("SELECT id FROM utenti WHERE username = ?", Long.class, username));
        }
        for (long userId : userIds.stream().distinct().toList()) {
            jdbc.update("DELETE FROM auth_sessions WHERE utente_id = ?", userId);
            jdbc.update("DELETE FROM movimenti_portafoglio WHERE portafoglio_id IN "
                    + "(SELECT id FROM portafogli WHERE utente_id = ?)", userId);
            jdbc.update("DELETE FROM inventario_utenti WHERE utente_id = ?", userId);
            jdbc.update("DELETE FROM portafogli WHERE utente_id = ?", userId);
            jdbc.update("DELETE FROM utenti WHERE id = ?", userId);
        }
    }
}
