package it.esercitazione.liveauction.producer.portafoglio.services;

import it.esercitazione.liveauction.producer.portafoglio.exceptions.PortafoglioException;
import it.esercitazione.liveauction.producer.portafoglio.model.TipoMovimento;
import it.esercitazione.liveauction.producer.portafoglio.repos.PortafoglioRepository;
import it.esercitazione.liveauction.producer.portafoglio.requests.ImpostaSaldoRequest;
import it.esercitazione.liveauction.producer.portafoglio.responses.PortafoglioResponse;
import it.esercitazione.liveauction.producer.portafoglio.responses.SaldoResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

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

    @Transactional
    @PreAuthorize("hasRole('USER') and authentication.token.subject == #p0.toString()")
    public SaldoResponse imposta(@Positive long utenteId, @NotNull @Valid ImpostaSaldoRequest request) {
        // Come i rilanci: utente prima del portafoglio, per serializzare l'eliminazione account.
        portafogli.verificaUtenteAttivo(utenteId, true);
        var saldo = portafogli.bloccaPortafogli(List.of(utenteId)).get(utenteId);
        BigDecimal totale = request.saldoTotale().setScale(2);
        if (totale.compareTo(saldo.riservato()) < 0) {
            throw new PortafoglioException(HttpStatus.CONFLICT, "SALDO_INFERIORE_AL_RISERVATO",
                    "Il saldo totale non può essere inferiore al saldo riservato");
        }
        BigDecimal differenza = totale.subtract(saldo.totale());
        // Un PUT identico non cambia versione o data e non crea movimenti di importo zero.
        if (differenza.signum() != 0) {
            saldo = portafogli.movimenta(saldo, null, TipoMovimento.IMPOSTAZIONE_SALDO,
                    differenza.abs(), differenza, BigDecimal.ZERO, Instant.now());
        }
        return new SaldoResponse(saldo.totale(), saldo.riservato(), saldo.disponibile(), "CRD");
    }
}
