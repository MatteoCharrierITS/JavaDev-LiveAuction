package it.esercitazione.liveauction.producer.prodotto.responses;

import it.esercitazione.liveauction.producer.prodotto.models.Prodotto;

import java.math.BigDecimal;
import java.time.Instant;

public record ProdottoResponse(
        Long id,
        String sku,
        String nome,
        String descrizione,
        BigDecimal prezzoFisso,
        boolean astabile,
        int quantitaDisponibile,
        int quantitaBloccata,
        long asteProgrammate,
        boolean attivo,
        CategoriaResponse categoria,
        long versione,
        Instant dataCreazione
) {
    public static ProdottoResponse from(Prodotto prodotto, long asteProgrammate) {
        return new ProdottoResponse(
                prodotto.getId(),
                prodotto.getSku(),
                prodotto.getNome(),
                prodotto.getDescrizione(),
                prodotto.getPrezzoFisso(),
                prodotto.isAstabile(),
                prodotto.getQuantitaDisponibile(),
                prodotto.getQuantitaBloccata(),
                asteProgrammate,
                prodotto.isAttivo(),
                CategoriaResponse.from(prodotto.getCategoria()),
                prodotto.getVersione(),
                prodotto.getDataCreazione()
        );
    }
}
