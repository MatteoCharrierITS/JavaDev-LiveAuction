package it.esercitazione.liveauction.producer.asta.services;

import it.esercitazione.liveauction.producer.asta.exceptions.AstaException;
import it.esercitazione.liveauction.producer.asta.models.Stato;
import it.esercitazione.liveauction.producer.asta.repos.AstaLetturaRepository;
import it.esercitazione.liveauction.producer.asta.repos.AstaLetturaRepository.DatiAsta;
import it.esercitazione.liveauction.producer.asta.responses.AstaSintesiResponse;
import it.esercitazione.liveauction.producer.asta.responses.AstaSintesiResponse.ProdottoSintesi;
import it.esercitazione.liveauction.producer.asta.responses.AstaSnapshotResponse;
import it.esercitazione.liveauction.producer.asta.responses.AstaSnapshotResponse.Partecipante;
import it.esercitazione.liveauction.producer.asta.responses.PaginaAsteResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

@Service
public class AstaLetturaService {

    private final AstaLetturaRepository repository;
    private final Clock clock;

    @Autowired
    public AstaLetturaService(AstaLetturaRepository repository) {
        this(repository, Clock.systemUTC());
    }

    AstaLetturaService(AstaLetturaRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    /** Lettura pubblica; conteggio e pagina condividono lo stesso snapshot PostgreSQL. */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PaginaAsteResponse cerca(Stato stato, String categoria, String query, int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw AstaException.parametriNonValidi();
        }
        var pagina = repository.cerca(stato, categoria, query, page, size);
        Instant adesso = clock.instant();
        return new PaginaAsteResponse(pagina.content().stream().map(a -> sintesi(a, adesso)).toList(),
                page, size, pagina.totale(), pagina.totale() / size + (pagina.totale() % size == 0 ? 0 : 1), adesso);
    }

    /** Snapshot pubblico: nessuna modifica allo stato e nessun dato riservato degli utenti. */
    @Transactional(readOnly = true)
    public AstaSnapshotResponse snapshot(long id) {
        var dati = repository.snapshot(id).orElseThrow(AstaException::astaNonTrovata);
        DatiAsta asta = dati.asta();
        Instant adesso = clock.instant();
        boolean chiusa = asta.stato() == Stato.CHIUSA;
        return new AstaSnapshotResponse(asta.id(), asta.stato(), prodotto(asta),
                asta.prezzoIniziale(), asta.incrementoMinimo(), asta.offertaCorrente(),
                partecipante(dati.offerenteId(), dati.offerenteUsername()), asta.numeroOfferte(),
                asta.inizioAt().minusSeconds(180), asta.inizioAt(), asta.fineAt(), adesso,
                offerteConsentite(asta, adesso), asta.sequence(),
                chiusa ? partecipante(dati.vincitoreId(), dati.vincitoreUsername()) : null,
                chiusa && dati.vincitoreId() != null ? asta.offertaCorrente() : null,
                chiusa ? dati.chiusaAt() : null);
    }

    private static AstaSintesiResponse sintesi(DatiAsta asta, Instant adesso) {
        return new AstaSintesiResponse(asta.id(), asta.stato(), prodotto(asta),
                asta.prezzoIniziale(), asta.incrementoMinimo(), asta.offertaCorrente(), asta.numeroOfferte(),
                asta.inizioAt().minusSeconds(180), asta.inizioAt(), asta.fineAt(),
                offerteConsentite(asta, adesso), asta.sequence());
    }

    private static ProdottoSintesi prodotto(DatiAsta asta) {
        return new ProdottoSintesi(asta.prodottoId(), asta.prodottoNome());
    }

    private static boolean offerteConsentite(DatiAsta asta, Instant adesso) {
        return asta.stato() == Stato.APERTA && !adesso.isBefore(asta.inizioAt()) && adesso.isBefore(asta.fineAt());
    }

    private static Partecipante partecipante(Long id, String username) {
        if (id == null) {
            return null;
        }
        String displayName = "***";
        if (username != null && username.codePointCount(0, username.length()) >= 2) {
            displayName = new String(Character.toChars(username.codePointAt(0))) + "***"
                    + new String(Character.toChars(username.codePointBefore(username.length())));
        }
        return new Partecipante(id, displayName);
    }
}
