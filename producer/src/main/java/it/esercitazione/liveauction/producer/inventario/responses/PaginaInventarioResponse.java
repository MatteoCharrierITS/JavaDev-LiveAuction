package it.esercitazione.liveauction.producer.inventario.responses;

import java.util.List;

public record PaginaInventarioResponse(List<Elemento> content, int page, int size,
                                       long totalElements, long totalPages) {
    public record Elemento(Prodotto prodotto, int quantita) {}
    public record Prodotto(long id, String sku, String nome, String descrizione,
                           boolean attivo, Categoria categoria) {}
    public record Categoria(long id, String nome, boolean attiva) {}
}
