package it.esercitazione.liveauction.consumer.admin;

import java.math.BigDecimal;

public record AdminProduct(Long id, String sku, String nome, String descrizione, BigDecimal prezzoFisso,
        boolean astabile, int quantitaDisponibile, int quantitaBloccata, long asteProgrammate,
        boolean attivo, AdminCategory categoria, long versione) {
    public boolean eligible() { return astabile && quantitaDisponibile > 0; }
}
