package it.esercitazione.liveauction.producer.asta.responses;

import io.swagger.v3.oas.annotations.media.Schema;
import it.esercitazione.liveauction.producer.asta.responses.AstaSintesiResponse.ProdottoSintesi;
import java.math.BigDecimal;
import java.time.Instant;

@Schema(description = "Asta vinta dall'utente.")

public record VittoriaResponse(@Schema(description = "ID dell'asta.", example = "18") long id, ProdottoSintesi prodotto, @Schema(description = "Prezzo pagato (CRD).", example = "75.00") BigDecimal prezzoFinale, @Schema(description = "Chiusura (UTC).", example = "2026-10-12T12:37:20Z") Instant chiusaAt) {}
