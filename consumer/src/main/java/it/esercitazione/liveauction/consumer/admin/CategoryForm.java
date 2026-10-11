package it.esercitazione.liveauction.consumer.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CategoryForm(@NotBlank @Size(max = 100) String nome,
        @NotBlank @Size(max = 100) @Pattern(regexp = "[a-z0-9]+(-[a-z0-9]+)*") String slug,
        @NotNull Boolean attiva) {
    public static CategoryForm from(AdminCategory category) {
        return new CategoryForm(category.nome(), category.slug(), category.attiva());
    }
}
