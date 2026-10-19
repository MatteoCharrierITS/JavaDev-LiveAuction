package it.esercitazione.liveauction.producer.inventario.services;

import it.esercitazione.liveauction.producer.inventario.responses.PaginaInventarioResponse;
import it.esercitazione.liveauction.producer.inventario.responses.PaginaInventarioResponse.*;
import it.esercitazione.liveauction.producer.portafoglio.exceptions.PortafoglioException;
import it.esercitazione.liveauction.producer.portafoglio.repos.PortafoglioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class InventarioService {
    private final JdbcTemplate jdbc;
    private final PortafoglioRepository utenti;

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    @PreAuthorize("hasRole('USER') and authentication.token.subject == #p0.toString()")
    public PaginaInventarioResponse personale(long utenteId, int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new PortafoglioException(HttpStatus.BAD_REQUEST, "PAGINAZIONE_NON_VALIDA",
                    "Pagina non negativa e dimensione tra 1 e 100 richieste");
        }
        utenti.verificaUtenteAttivo(utenteId, false);
        long totale = jdbc.queryForObject("SELECT count(*) FROM inventario_utenti WHERE utente_id=? AND quantita>0",
                Long.class, utenteId);
        var content = jdbc.query("""
                SELECT p.id, p.sku, p.nome, p.descrizione, p.immagine_url, p.attivo, i.quantita,
                       c.id AS categoria_id, c.nome AS categoria_nome, c.attiva
                FROM inventario_utenti i JOIN prodotti p ON p.id=i.prodotto_id
                JOIN categorie c ON c.id=p.categoria_id
                WHERE i.utente_id=? AND i.quantita>0 ORDER BY p.id LIMIT ? OFFSET ?
                """, (rs, row) -> new Elemento(new Prodotto(rs.getLong("id"), rs.getString("sku"),
                rs.getString("nome"), rs.getString("descrizione"), rs.getString("immagine_url"), rs.getBoolean("attivo"),
                new Categoria(rs.getLong("categoria_id"), rs.getString("categoria_nome"), rs.getBoolean("attiva"))),
                rs.getInt("quantita")), utenteId, size, (long) page * size);
        return new PaginaInventarioResponse(content, page, size, totale,
                totale / size + (totale % size == 0 ? 0 : 1));
    }
}
