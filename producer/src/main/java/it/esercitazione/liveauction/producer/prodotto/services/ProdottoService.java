package it.esercitazione.liveauction.producer.prodotto.services;

import it.esercitazione.liveauction.producer.prodotto.exceptions.CatalogoException;
import it.esercitazione.liveauction.producer.prodotto.models.Categoria;
import it.esercitazione.liveauction.producer.prodotto.models.Prodotto;
import it.esercitazione.liveauction.producer.prodotto.repos.CategoriaRepository;
import it.esercitazione.liveauction.producer.prodotto.repos.ProdottoRepository;
import it.esercitazione.liveauction.producer.prodotto.repos.ProdottoRepository.ConteggioAste;
import it.esercitazione.liveauction.producer.prodotto.requests.ModificaProdottoRequest;
import it.esercitazione.liveauction.producer.prodotto.requests.NuovoProdottoRequest;
import it.esercitazione.liveauction.producer.prodotto.responses.PaginaResponse;
import it.esercitazione.liveauction.producer.prodotto.responses.ProdottoResponse;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProdottoService {

    private final ProdottoRepository prodottoRepository;
    private final CategoriaRepository categoriaRepository;

    /** Catalogo pubblico: soltanto prodotti attivi appartenenti a categorie attive. */
    @Transactional(readOnly = true)
    public PaginaResponse<ProdottoResponse> cercaCatalogo(String query, String categoria, Boolean astabile,
                                                          int page, int size) {
        return cerca(query, categoria, astabile, true, true, page, size);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('ADMIN')")
    public PaginaResponse<ProdottoResponse> cercaAdmin(String query, String categoria, Boolean astabile,
                                                       Boolean attivo, int page, int size) {
        return cerca(query, categoria, astabile, attivo, false, page, size);
    }

    @Transactional(readOnly = true)
    public ProdottoResponse dettaglioCatalogo(long id) {
        Prodotto prodotto = prodottoRepository.findWithCategoriaById(id)
                .filter(Prodotto::isAttivo)
                .filter(candidato -> candidato.getCategoria().isAttiva())
                .orElseThrow(ProdottoService::prodottoNonTrovato);
        return conConteggioAste(prodotto);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('ADMIN')")
    public ProdottoResponse dettaglioAdmin(long id) {
        return conConteggioAste(trovaProdotto(id));
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public ProdottoResponse crea(NuovoProdottoRequest request) {
        Categoria categoria = trovaCategoria(request.categoriaId());
        String sku = normalizzaSku(request.sku());
        if (prodottoRepository.existsBySku(sku)) {
            throw skuGiaUtilizzato(null);
        }

        var prodotto = new Prodotto();
        prodotto.setCategoria(categoria);
        prodotto.setSku(sku);
        prodotto.setNome(request.nome().trim());
        prodotto.setDescrizione(normalizzaDescrizione(request.descrizione()));
        prodotto.setPrezzoFisso(request.prezzoFisso());
        prodotto.setAstabile(request.astabile());
        prodotto.setQuantitaDisponibile(request.quantitaDisponibile());
        return ProdottoResponse.from(salva(prodotto), 0);
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public ProdottoResponse modifica(long id, ModificaProdottoRequest request) {
        Prodotto prodotto = trovaProdotto(id);
        // Lo stock può cambiare per una programmazione d'asta tra la lettura dell'ADMIN e il salvataggio.
        if (prodotto.getVersione() != request.versione()) {
            throw versioneNonAggiornata(null);
        }
        String sku = normalizzaSku(request.sku());
        if (prodottoRepository.existsBySkuAndIdNot(sku, id)) {
            throw skuGiaUtilizzato(null);
        }

        if (!prodotto.getCategoria().getId().equals(request.categoriaId())) {
            prodotto.setCategoria(trovaCategoria(request.categoriaId()));
        }
        prodotto.setSku(sku);
        prodotto.setNome(request.nome().trim());
        prodotto.setDescrizione(normalizzaDescrizione(request.descrizione()));
        prodotto.setPrezzoFisso(request.prezzoFisso());
        prodotto.setAstabile(request.astabile());
        prodotto.setQuantitaDisponibile(request.quantitaDisponibile());
        prodotto.setAttivo(request.attivo());
        return conConteggioAste(salva(prodotto));
    }

    private PaginaResponse<ProdottoResponse> cerca(String query, String categoria, Boolean astabile,
                                                   Boolean attivo, boolean soloCategorieAttive, int page, int size) {
        Page<Prodotto> risultati = prodottoRepository.findAll(
                filtro(query, categoria, astabile, attivo, soloCategorieAttive),
                PageRequest.of(page, size, Sort.by("nome", "id")));
        Map<Long, Long> aste = contaAste(risultati.getContent());
        List<ProdottoResponse> contenuto = risultati.getContent().stream()
                .map(prodotto -> ProdottoResponse.from(prodotto, aste.getOrDefault(prodotto.getId(), 0L)))
                .toList();
        return new PaginaResponse<>(contenuto, page, size, risultati.getTotalElements(), risultati.getTotalPages());
    }

    private static Specification<Prodotto> filtro(String query, String categoria, Boolean astabile,
                                                  Boolean attivo, boolean soloCategorieAttive) {
        return (root, criteria, cb) -> {
            List<Predicate> predicati = new ArrayList<>();
            if (soloCategorieAttive) {
                predicati.add(cb.isTrue(root.get("categoria").get("attiva")));
            }
            if (query != null && !query.isBlank()) {
                String pattern = "%" + escapeLike(query.trim().toLowerCase(Locale.ROOT)) + "%";
                predicati.add(cb.or(
                        cb.like(cb.lower(root.get("nome")), pattern, '\\'),
                        cb.like(cb.lower(root.get("sku")), pattern, '\\'),
                        cb.like(cb.lower(root.get("descrizione")), pattern, '\\')));
            }
            if (categoria != null && !categoria.isBlank()) {
                predicati.add(cb.equal(root.get("categoria").get("slug"), categoria.trim()));
            }
            if (astabile != null) {
                predicati.add(cb.equal(root.get("astabile"), astabile));
            }
            if (attivo != null) {
                predicati.add(cb.equal(root.get("attivo"), attivo));
            }
            return cb.and(predicati.toArray(Predicate[]::new));
        };
    }

    private ProdottoResponse conConteggioAste(Prodotto prodotto) {
        return ProdottoResponse.from(prodotto, contaAste(List.of(prodotto)).getOrDefault(prodotto.getId(), 0L));
    }

    private Map<Long, Long> contaAste(List<Prodotto> prodotti) {
        if (prodotti.isEmpty()) {
            return Map.of();
        }
        List<Long> ids = prodotti.stream().map(Prodotto::getId).toList();
        return prodottoRepository.contaAsteNonConcluse(ids).stream()
                .collect(Collectors.toMap(ConteggioAste::getProdottoId, ConteggioAste::getTotale));
    }

    private Prodotto trovaProdotto(long id) {
        return prodottoRepository.findWithCategoriaById(id)
                .orElseThrow(ProdottoService::prodottoNonTrovato);
    }

    private Categoria trovaCategoria(long id) {
        return categoriaRepository.findById(id)
                .orElseThrow(() -> CatalogoException.nonTrovato("Categoria non trovata"));
    }

    private Prodotto salva(Prodotto prodotto) {
        try {
            return prodottoRepository.saveAndFlush(prodotto);
        } catch (OptimisticLockingFailureException exception) {
            throw versioneNonAggiornata(exception);
        } catch (DataIntegrityViolationException exception) {
            throw skuGiaUtilizzato(exception);
        }
    }

    private static String normalizzaSku(String sku) {
        return sku.trim().toUpperCase(Locale.ROOT);
    }

    private static String normalizzaDescrizione(String descrizione) {
        return descrizione == null || descrizione.isBlank() ? null : descrizione.trim();
    }

    private static String escapeLike(String testo) {
        return testo.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private static CatalogoException prodottoNonTrovato() {
        return CatalogoException.nonTrovato("Prodotto non trovato");
    }

    private static CatalogoException skuGiaUtilizzato(Throwable causa) {
        return new CatalogoException(HttpStatus.CONFLICT, "SKU_GIA_UTILIZZATO", "SKU già utilizzato", causa);
    }

    private static CatalogoException versioneNonAggiornata(Throwable causa) {
        return new CatalogoException(HttpStatus.CONFLICT, "VERSIONE_NON_AGGIORNATA",
                "Il prodotto è stato modificato nel frattempo: ricaricarlo e riprovare", causa);
    }
}
