package it.esercitazione.liveauction.producer.prodotto.requests;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;

/**
 * Sostituisce tutti i campi modificabili. {@code quantitaBloccata} resta gestita dalle aste;
 * {@code versione} deve corrispondere a quella letta, per non sovrascrivere stock cambiato nel frattempo.
 */
public record ModificaProdottoRequest(
        @NotNull @Positive Long categoriaId,
        @NotBlank @Size(max = 30) String sku,
        @NotBlank @Size(max = 200) String nome,
        @Size(max = 5000) String descrizione,
        @Positive @Digits(integer = 10, fraction = 2) BigDecimal prezzoFisso,
        @NotNull Boolean astabile,
        @NotNull @PositiveOrZero Integer quantitaDisponibile,
        @NotNull Boolean attivo,
        @NotNull @PositiveOrZero Long versione
) {
}
