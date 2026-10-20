package it.esercitazione.liveauction.producer.portafoglio.services;

import it.esercitazione.liveauction.producer.portafoglio.exceptions.PortafoglioException;
import it.esercitazione.liveauction.producer.portafoglio.repos.PortafoglioRepository;
import it.esercitazione.liveauction.producer.portafoglio.responses.PortafoglioResponse;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

@Service
@Validated
@RequiredArgsConstructor
public class PortafoglioService {
    private final PortafoglioRepository portafogli;

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    @PreAuthorize("hasRole('USER') and authentication.token.subject == #p0.toString()")
    public PortafoglioResponse leggi(@Positive long utenteId, int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new PortafoglioException(HttpStatus.BAD_REQUEST, "PAGINAZIONE_NON_VALIDA",
                    "page deve essere >= 0 e size compresa tra 1 e 100");
        }
        portafogli.verificaUtenteAttivo(utenteId, false);
        var saldo = portafogli.leggi(utenteId);
        long count = portafogli.contaMovimenti(saldo.id());
        return new PortafoglioResponse(saldo.totale(), saldo.riservato(), saldo.disponibile(), "CRD",
                portafogli.movimenti(saldo.id(), page, size), page, size, count,
                count / size + (count % size == 0 ? 0 : 1));
    }

}
