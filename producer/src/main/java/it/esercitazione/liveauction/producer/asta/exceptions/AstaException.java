package it.esercitazione.liveauction.producer.asta.exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

/** Errore REST delle aste con il codice previsto dal contratto. */
public class AstaException extends ErrorResponseException {

    public AstaException(HttpStatus status, String codice, String dettaglio) {
        super(status, problema(status, codice, dettaglio), null);
    }

    public static AstaException dataInizioNonValida(String dettaglio) {
        return new AstaException(HttpStatus.UNPROCESSABLE_ENTITY, "DATA_INIZIO_NON_VALIDA", dettaglio);
    }

    public static AstaException prodottoNonTrovato() {
        return new AstaException(HttpStatus.NOT_FOUND, "RISORSA_NON_TROVATA", "Prodotto non trovato");
    }

    public static AstaException astaNonTrovata() {
        return new AstaException(HttpStatus.NOT_FOUND, "RISORSA_NON_TROVATA", "Asta non trovata");
    }

    public static AstaException parametriNonValidi() {
        return new AstaException(HttpStatus.BAD_REQUEST, "PARAMETRI_NON_VALIDI",
                "La pagina deve essere non negativa e la dimensione compresa tra 1 e 100");
    }

    public static AstaException prodottoNonAstabile() {
        return new AstaException(HttpStatus.UNPROCESSABLE_ENTITY, "PRODOTTO_NON_ASTABILE",
                "Il prodotto non è abilitato alle aste");
    }

    public static AstaException prodottoNonDisponibile() {
        return new AstaException(HttpStatus.CONFLICT, "PRODOTTO_NON_DISPONIBILE",
                "Il prodotto non ha unità disponibili");
    }

    public static AstaException prezzoInizialeNonValido() {
        return new AstaException(HttpStatus.UNPROCESSABLE_ENTITY, "PREZZO_INIZIALE_NON_VALIDO",
                "Il prezzo iniziale deve essere positivo, con massimo 10 cifre intere e 2 decimali");
    }

    public static AstaException adminNonConsentito() {
        return new AstaException(HttpStatus.FORBIDDEN, "OPERAZIONE_NON_CONSENTITA",
                "La programmazione richiede un ADMIN attivo");
    }

    private static ProblemDetail problema(HttpStatus status, String codice, String dettaglio) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, dettaglio);
        problem.setProperty("code", codice);
        return problem;
    }
}
