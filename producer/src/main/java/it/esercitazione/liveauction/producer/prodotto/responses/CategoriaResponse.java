package it.esercitazione.liveauction.producer.prodotto.responses;

import it.esercitazione.liveauction.producer.prodotto.models.Categoria;

public record CategoriaResponse(
        Long id,
        String nome,
        String slug,
        boolean attiva
) {
    public static CategoriaResponse from(Categoria categoria) {
        return new CategoriaResponse(categoria.getId(), categoria.getNome(), categoria.getSlug(), categoria.isAttiva());
    }
}
