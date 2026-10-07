package it.esercitazione.liveauction.producer.prodotto.exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

/** Errore REST del catalogo, serializzato come problem+json con il campo {@code code}. */
public class CatalogoException extends ErrorResponseException {

    public CatalogoException(HttpStatus status, String codice, String dettaglio) {
        this(status, codice, dettaglio, null);
    }

    public CatalogoException(HttpStatus status, String codice, String dettaglio, Throwable causa) {
        super(status, problema(status, codice, dettaglio), causa);
    }

    public static CatalogoException nonTrovato(String dettaglio) {
        return new CatalogoException(HttpStatus.NOT_FOUND, "RISORSA_NON_TROVATA", dettaglio);
    }

    private static ProblemDetail problema(HttpStatus status, String codice, String dettaglio) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, dettaglio);
        problem.setProperty("code", codice);
        return problem;
    }
}
