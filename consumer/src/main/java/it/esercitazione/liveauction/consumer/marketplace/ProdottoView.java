package it.esercitazione.liveauction.consumer.marketplace;

import java.math.BigDecimal;

public record ProdottoView(Long id, String nome, String descrizione, BigDecimal prezzoFisso,
                           boolean astabile, int quantitaDisponibile, int quantitaBloccata,
                           long asteProgrammate, CategoriaView categoria) {}
