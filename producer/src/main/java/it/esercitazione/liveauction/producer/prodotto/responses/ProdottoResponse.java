package it.esercitazione.liveauction.producer.prodotto.responses;

import io.swagger.v3.oas.annotations.media.Schema;
import it.esercitazione.liveauction.producer.prodotto.models.Prodotto;

import java.math.BigDecimal;
import java.time.Instant;

@Schema(description = "Prodotto del catalogo con stock e stato.")

public record ProdottoResponse(
        @Schema(description = "ID del prodotto.", example = "1") Long id,
        @Schema(description = "Codice univoco.", example = "INF-LAP-001") String sku,
        @Schema(description = "Nome.", example = "Laptop Pro 15") String nome,
        @Schema(description = "Descrizione, può essere null.", example = "Laptop per lo studio") String descrizione,
        @Schema(description = "Prezzo di acquisto diretto in CRD; null = non acquistabile.", example = "1299.90") BigDecimal prezzoFisso,
        @Schema(description = "Campo persistito: il prodotto può essere messo all'asta (serve anche `quantitaDisponibile` > 0).", example = "true") boolean astabile,
        @Schema(description = "Unità libere.", example = "3") int quantitaDisponibile,
        @Schema(description = "Unità riservate ad aste programmate o in corso.", example = "1") int quantitaBloccata,
        @Schema(description = "Aste in `PROGRAMMATA`, `STANZA_APERTA` o `APERTA`.", example = "1") long asteProgrammate,
        @Schema(description = "false = disattivato.", example = "true") boolean attivo,
        CategoriaResponse categoria,
        @Schema(description = "Versione per il controllo di concorrenza del PUT.", example = "0") long versione,
        @Schema(description = "Creazione (UTC).", example = "2026-10-07T08:00:00Z") Instant dataCreazione
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
