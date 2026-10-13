package it.esercitazione.liveauction.producer.asta.responses;

import it.esercitazione.liveauction.producer.asta.models.Stato;
import it.esercitazione.liveauction.producer.asta.responses.AstaSintesiResponse.ProdottoSintesi;
import java.math.BigDecimal;
import java.time.Instant;

public record StoricoAstaResponse(long id, Stato stato, ProdottoSintesi prodotto, Long vincitoreId,
                                  BigDecimal prezzoFinale, Instant inizioAt, Instant fineAt,
                                  Instant conclusaAt) {}
