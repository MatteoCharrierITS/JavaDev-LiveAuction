package it.esercitazione.liveauction.producer.asta.exceptions;

import com.fasterxml.jackson.databind.JsonMappingException;
import it.esercitazione.liveauction.producer.asta.AdminAsteController;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Traduce gli errori di data e prezzo nei codici del contratto delle aste. */
@RestControllerAdvice(assignableTypes = AdminAsteController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AstaValidationHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> gestisciValidazione(MethodArgumentNotValidException exception) {
        if (exception.getBindingResult().hasFieldErrors("inizioLocale")
                || exception.getBindingResult().hasFieldErrors("timeZone")) {
            return errore(AstaException.dataInizioNonValida(
                    "Data e ora sono obbligatorie e il fuso deve essere Europe/Rome"));
        }
        if (exception.getBindingResult().hasFieldErrors("prezzoIniziale")) {
            return errore(AstaException.prezzoInizialeNonValido());
        }
        return richiestaNonValida("Richiesta non valida: controllare i campi obbligatori");
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ProblemDetail> gestisciJson(HttpMessageNotReadableException exception) {
        if (exception.getCause() instanceof JsonMappingException mapping && !mapping.getPath().isEmpty()) {
            String campo = mapping.getPath().getLast().getFieldName();
            if ("inizioLocale".equals(campo) || "timeZone".equals(campo)) {
                return errore(AstaException.dataInizioNonValida(
                        "Data locale o fuso orario in formato non valido"));
            }
            if ("prezzoIniziale".equals(campo)) {
                return errore(AstaException.prezzoInizialeNonValido());
            }
        }
        return richiestaNonValida("Body JSON mancante o non valido");
    }

    private static ResponseEntity<ProblemDetail> errore(AstaException errore) {
        return ResponseEntity.status(errore.getStatusCode()).body(errore.getBody());
    }

    private static ResponseEntity<ProblemDetail> richiestaNonValida(String dettaglio) {
        return ResponseEntity.badRequest().body(ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, dettaglio));
    }
}
