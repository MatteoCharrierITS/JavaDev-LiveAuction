package it.esercitazione.liveauction.producer.inventario.responses;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "Pagina dell'inventario personale.")

public record PaginaInventarioResponse(@Schema(description = "Righe della pagina, per ID prodotto crescente.") List<Elemento> content, @Schema(description = "Indice della pagina (da 0).", example = "0") int page, @Schema(description = "Dimensione richiesta.", example = "20") int size,
                                       @Schema(description = "Righe totali.", example = "1") long totalElements, @Schema(description = "Pagine totali.", example = "1") long totalPages) {
    @Schema(description = "Riga di inventario: prodotto e quantità.")
    public record Elemento(Prodotto prodotto, @Schema(description = "Unità possedute (> 0).", example = "2") int quantita) {}
    @Schema(description = "Prodotto posseduto.")
    public record Prodotto(@Schema(description = "ID del prodotto.", example = "3") long id, @Schema(description = "Codice del prodotto.", example = "KEY-001") String sku, @Schema(description = "Nome del prodotto.", example = "Tastiera") String nome, @Schema(description = "Descrizione, può essere null.") String descrizione,
                           @Schema(description = "Il prodotto può non essere più attivo.", example = "true") boolean attivo, Categoria categoria) {}
    @Schema(description = "Categoria del prodotto posseduto.")
    public record Categoria(@Schema(description = "ID della categoria.", example = "1") long id, @Schema(description = "Nome della categoria.", example = "Hardware") String nome, @Schema(description = "La categoria può non essere più attiva.", example = "true") boolean attiva) {}
}
