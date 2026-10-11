package it.esercitazione.liveauction.consumer.admin;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public record ProductForm(@NotNull @Positive Long categoriaId,
        @NotBlank @Size(max = 30) String sku,
        @NotBlank @Size(max = 200) String nome,
        @Size(max = 5000) String descrizione,
        @Positive @Digits(integer = 10, fraction = 2) BigDecimal prezzoFisso,
        @NotNull Boolean astabile,
        @NotNull @PositiveOrZero Integer quantitaDisponibile,
        @NotNull Boolean attivo,
        @NotNull @PositiveOrZero Long versione) {
    public static ProductForm empty() { return new ProductForm(null, "", "", "", null, false, 0, true, 0L); }
    public static ProductForm from(AdminProduct product) {
        return new ProductForm(product.categoria().id(), product.sku(), product.nome(), product.descrizione(),
                product.prezzoFisso(), product.astabile(), product.quantitaDisponibile(), product.attivo(), product.versione());
    }
    public CreateRequest createRequest() {
        return new CreateRequest(categoriaId, sku, nome, descrizione, prezzoFisso, astabile, quantitaDisponibile);
    }
    public UpdateRequest updateRequest() {
        return new UpdateRequest(categoriaId, sku, nome, descrizione, prezzoFisso, astabile,
                quantitaDisponibile, attivo, versione);
    }
    public record CreateRequest(Long categoriaId, String sku, String nome, String descrizione,
            BigDecimal prezzoFisso, Boolean astabile, Integer quantitaDisponibile) {}
    public record UpdateRequest(Long categoriaId, String sku, String nome, String descrizione,
            BigDecimal prezzoFisso, Boolean astabile, Integer quantitaDisponibile, Boolean attivo, Long versione) {}
}
