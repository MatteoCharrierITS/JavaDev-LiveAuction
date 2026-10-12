package it.esercitazione.liveauction.producer.asta.services;

import it.esercitazione.liveauction.producer.asta.exceptions.AstaException;
import it.esercitazione.liveauction.producer.asta.models.Stato;
import it.esercitazione.liveauction.producer.asta.repos.StoricoAstaRepository;
import it.esercitazione.liveauction.producer.asta.responses.PaginaStoricoResponse;
import it.esercitazione.liveauction.producer.asta.responses.StoricoAstaResponse;
import it.esercitazione.liveauction.producer.asta.responses.VittoriaResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class StoricoAstaService {
    private final StoricoAstaRepository repository;

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    @PreAuthorize("hasRole('USER') and authentication.token.subject == #p0.toString()")
    public PaginaStoricoResponse<VittoriaResponse> vittorie(long utenteId, int page, int size) {
        validaPagina(page, size);
        var dati = repository.cerca(true, null, utenteId, null, null, null, page, size);
        return new PaginaStoricoResponse<>(dati.content().stream().map(v ->
                new VittoriaResponse(v.id(), v.prodotto(), v.prezzoFinale(), v.conclusaAt())).toList(),
                page, size, dati.totale(), pagine(dati.totale(), size));
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    @PreAuthorize("hasRole('ADMIN')")
    public PaginaStoricoResponse<StoricoAstaResponse> storico(Long prodottoId, Long vincitoreId, Stato stato,
                                                             Instant da, Instant a, int page, int size) {
        validaPagina(page, size);
        if ((prodottoId != null && prodottoId <= 0) || (vincitoreId != null && vincitoreId <= 0)
                || (stato != null && stato != Stato.CHIUSA && stato != Stato.ANNULLATA)
                || (da != null && a != null && da.isAfter(a))) {
            throw new AstaException(HttpStatus.BAD_REQUEST, "PARAMETRI_NON_VALIDI",
                    "ID positivi, stato CHIUSA/ANNULLATA e intervallo da <= a richiesti");
        }
        var dati = repository.cerca(false, prodottoId, vincitoreId, stato, da, a, page, size);
        return new PaginaStoricoResponse<>(dati.content(), page, size, dati.totale(), pagine(dati.totale(), size));
    }

    private static void validaPagina(int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw AstaException.parametriNonValidi();
        }
    }

    private static long pagine(long totale, int size) {
        return totale / size + (totale % size == 0 ? 0 : 1);
    }
}
