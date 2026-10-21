package it.esercitazione.liveauction.producer.prodotto.requests;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;

@Schema(description = "Dati per creare un prodotto.")

public record NuovoProdottoRequest(
        @NotNull @Positive @Schema(description = "ID di una categoria esistente.", example = "1") Long categoriaId,
        @NotBlank @Size(max = 30) @Schema(description = "Codice univoco, max 30 caratteri; normalizzato in maiuscolo.", example = "INF-LAP-001") String sku,
        @NotBlank @Size(max = 200) @Schema(description = "Nome, max 200 caratteri.", example = "Laptop Pro 15") String nome,
        @Size(max = 5000) @Schema(description = "Facoltativa, max 5000 caratteri.", example = "Laptop per lo studio") String descrizione,
        @Positive @Digits(integer = 10, fraction = 2) @Schema(description = "Facoltativo; se assente il prodotto non è acquistabile. Positivo, max 10 cifre intere e 2 decimali (CRD).", example = "1299.90") BigDecimal prezzoFisso,
        @NotNull @Schema(description = "Se true il prodotto può essere messo all'asta.", example = "true") Boolean astabile,
        @NotNull @PositiveOrZero @Schema(description = "Unità in magazzino, non negativa.", example = "3") Integer quantitaDisponibile
) {
}
