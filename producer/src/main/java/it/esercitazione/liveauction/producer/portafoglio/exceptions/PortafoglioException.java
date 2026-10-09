package it.esercitazione.liveauction.producer.portafoglio.exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

public class PortafoglioException extends ErrorResponseException {
    public PortafoglioException(HttpStatus status, String code, String detail) {
        super(status, problema(status, code, detail), null);
    }

    private static ProblemDetail problema(HttpStatus status, String code, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setProperty("code", code);
        return problem;
    }
}
