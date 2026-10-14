package it.esercitazione.liveauction.producer.acquisto;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

public class AcquistoException extends ErrorResponseException {
    public AcquistoException(HttpStatus status, String code, String detail) {
        super(status, problem(status,code,detail),null);
    }
    private static ProblemDetail problem(HttpStatus status,String code,String detail) {
        var p=ProblemDetail.forStatusAndDetail(status,detail); p.setProperty("code",code); return p;
    }
}
