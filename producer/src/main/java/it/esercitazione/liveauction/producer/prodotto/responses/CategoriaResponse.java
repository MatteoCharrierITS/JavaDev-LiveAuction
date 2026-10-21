package it.esercitazione.liveauction.producer.prodotto.responses;

import io.swagger.v3.oas.annotations.media.Schema;
import it.esercitazione.liveauction.producer.prodotto.models.Categoria;

@Schema(description = "Categoria di prodotti.")

public record CategoriaResponse(
        @Schema(description = "ID della categoria.", example = "1") Long id,
        @Schema(description = "Nome.", example = "Informatica") String nome,
        @Schema(description = "Slug univoco.", example = "informatica") String slug,
        @Schema(description = "false = nascosta al pubblico.", example = "true") boolean attiva
) {
    public static CategoriaResponse from(Categoria categoria) {
        return new CategoriaResponse(categoria.getId(), categoria.getNome(), categoria.getSlug(), categoria.isAttiva());
    }
}
