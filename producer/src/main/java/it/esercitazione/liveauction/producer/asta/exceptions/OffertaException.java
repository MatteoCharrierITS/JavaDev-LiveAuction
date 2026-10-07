package it.esercitazione.liveauction.producer.asta.exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

public class OffertaException extends ErrorResponseException {
    public OffertaException(HttpStatus status, String code, String dettaglio) {
        super(status, problema(status, code, dettaglio), null);
    }

    private static ProblemDetail problema(HttpStatus status, String code, String dettaglio) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, dettaglio);
        problem.setProperty("code", code);
        return problem;
    }
}
