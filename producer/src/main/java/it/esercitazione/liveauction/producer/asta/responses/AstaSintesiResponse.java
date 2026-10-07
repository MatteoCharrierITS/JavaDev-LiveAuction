package it.esercitazione.liveauction.producer.asta.responses;

import it.esercitazione.liveauction.producer.asta.models.Stato;

import java.math.BigDecimal;
import java.time.Instant;

public record AstaSintesiResponse(
        Long id, Stato stato, ProdottoSintesi prodotto,
        BigDecimal prezzoIniziale, BigDecimal incrementoMinimo, BigDecimal offertaCorrente, long numeroOfferte,
        Instant aperturaStanzaAt, Instant inizioAt, Instant fineAt,
        boolean offerteConsentite, long sequence) {

    public record ProdottoSintesi(Long id, String nome) {
    }
}
