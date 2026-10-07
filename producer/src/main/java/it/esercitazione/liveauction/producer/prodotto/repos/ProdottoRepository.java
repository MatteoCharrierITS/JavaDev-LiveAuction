package it.esercitazione.liveauction.producer.prodotto.repos;

import it.esercitazione.liveauction.producer.prodotto.models.Prodotto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ProdottoRepository extends JpaRepository<Prodotto, Long>, JpaSpecificationExecutor<Prodotto> {

    // Carica la categoria nella stessa query per evitare N+1 durante la mappatura in DTO.
    @Override
    @EntityGraph(attributePaths = "categoria")
    Page<Prodotto> findAll(Specification<Prodotto> spec, Pageable pageable);

    @EntityGraph(attributePaths = "categoria")
    Optional<Prodotto> findWithCategoriaById(Long id);

    boolean existsBySku(String sku);
    boolean existsBySkuAndIdNot(String sku, Long id);

    // Aste che tengono bloccata una unità: tutte quelle non ancora chiuse o annullate.
    @Query(value = """
            SELECT prodotto_id AS "prodottoId", COUNT(*) AS "totale"
            FROM aste
            WHERE prodotto_id IN (:ids)
              AND stato IN ('PROGRAMMATA', 'STANZA_APERTA', 'APERTA')
            GROUP BY prodotto_id
            """, nativeQuery = true)
    List<ConteggioAste> contaAsteNonConcluse(@Param("ids") Collection<Long> ids);

    interface ConteggioAste {
        Long getProdottoId();
        Long getTotale();
    }

}
