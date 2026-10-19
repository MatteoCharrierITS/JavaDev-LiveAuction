package it.esercitazione.liveauction.producer.prodotto.requests;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public record NuovoProdottoRequest(
        @NotNull @Positive Long categoriaId,
        @NotBlank @Size(max = 30) String sku,
        @NotBlank @Size(max = 200) String nome,
        @Size(max = 5000) String descrizione,
        // Facoltativo: URL http(s) dell'immagine; null o vuoto = nessuna immagine.
        @Size(max = 500) @Pattern(regexp = "(https?://\\S+)?", message = "deve essere un URL http o https") String immagineUrl,
        @Positive @Digits(integer = 10, fraction = 2) BigDecimal prezzoFisso,
        @NotNull Boolean astabile,
        @NotNull @PositiveOrZero Integer quantitaDisponibile
) {
}
