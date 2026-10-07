package it.esercitazione.liveauction.producer.prodotto.services;

import it.esercitazione.liveauction.producer.prodotto.exceptions.CatalogoException;
import it.esercitazione.liveauction.producer.prodotto.models.Categoria;
import it.esercitazione.liveauction.producer.prodotto.repos.CategoriaRepository;
import it.esercitazione.liveauction.producer.prodotto.requests.CategoriaRequest;
import it.esercitazione.liveauction.producer.prodotto.responses.CategoriaResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CategoriaService {

    private final CategoriaRepository categoriaRepository;

    @Transactional(readOnly = true)
    public List<CategoriaResponse> elencaAttive() {
        return categoriaRepository.findByAttivaTrueOrderByNomeAsc().stream()
                .map(CategoriaResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('ADMIN')")
    public List<CategoriaResponse> elencaTutte() {
        return categoriaRepository.findAllByOrderByNomeAsc().stream()
                .map(CategoriaResponse::from)
                .toList();
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public CategoriaResponse crea(CategoriaRequest request) {
        String nome = request.nome().trim();
        if (categoriaRepository.existsByNome(nome) || categoriaRepository.existsBySlug(request.slug())) {
            throw categoriaGiaEsistente(null);
        }

        var categoria = new Categoria();
        categoria.setNome(nome);
        categoria.setSlug(request.slug());
        categoria.setAttiva(request.attiva() == null || request.attiva());
        return salva(categoria);
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public CategoriaResponse modifica(long id, CategoriaRequest request) {
        Categoria categoria = categoriaRepository.findById(id)
                .orElseThrow(() -> CatalogoException.nonTrovato("Categoria non trovata"));
        String nome = request.nome().trim();
        if (categoriaRepository.existsByNomeAndIdNot(nome, id)
                || categoriaRepository.existsBySlugAndIdNot(request.slug(), id)) {
            throw categoriaGiaEsistente(null);
        }

        categoria.setNome(nome);
        categoria.setSlug(request.slug());
        if (request.attiva() != null) {
            categoria.setAttiva(request.attiva());
        }
        return salva(categoria);
    }

    private CategoriaResponse salva(Categoria categoria) {
        try {
            return CategoriaResponse.from(categoriaRepository.saveAndFlush(categoria));
        } catch (DataIntegrityViolationException exception) {
            throw categoriaGiaEsistente(exception);
        }
    }

    private static CatalogoException categoriaGiaEsistente(Throwable causa) {
        return new CatalogoException(HttpStatus.CONFLICT, "CATEGORIA_GIA_ESISTENTE",
                "Nome o slug della categoria già utilizzati", causa);
    }
}
