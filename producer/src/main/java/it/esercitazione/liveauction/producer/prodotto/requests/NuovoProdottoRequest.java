package it.esercitazione.liveauction.producer.prodotto.requests;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public record NuovoProdottoRequest(
        @NotNull @Positive Long categoriaId,
        @NotBlank @Size(max = 30) String sku,
        @NotBlank @Size(max = 200) String nome,
        @Size(max = 5000) String descrizione,
        @Positive @Digits(integer = 10, fraction = 2) BigDecimal prezzoFisso,
        @NotNull Boolean astabile,
        @NotNull @PositiveOrZero Integer quantitaDisponibile
) {
}
