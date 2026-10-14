package it.esercitazione.liveauction.consumer.inventario;

import java.util.List;

public record InventoryPage(List<Item> content, int page, int size, long totalElements, long totalPages) {
    public record Item(Product prodotto, int quantita) {}
    public record Product(long id, String sku, String nome, String descrizione, boolean attivo, Category categoria) {}
    public record Category(long id, String nome, boolean attiva) {}
}
