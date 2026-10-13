package it.esercitazione.liveauction.producer.asta.responses;

import it.esercitazione.liveauction.producer.asta.responses.AstaSintesiResponse.ProdottoSintesi;
import java.math.BigDecimal;
import java.time.Instant;

public record VittoriaResponse(long id, ProdottoSintesi prodotto, BigDecimal prezzoFinale, Instant chiusaAt) {}
