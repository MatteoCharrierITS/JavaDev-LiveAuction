package it.esercitazione.liveauction.producer.prodotto.repos;

import it.esercitazione.liveauction.producer.prodotto.models.Categoria;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CategoriaRepository extends JpaRepository<Categoria, Long> {

    List<Categoria> findByAttivaTrueOrderByNomeAsc();
    List<Categoria> findAllByOrderByNomeAsc();
    boolean existsByNome(String nome);
    boolean existsBySlug(String slug);
    boolean existsByNomeAndIdNot(String nome, Long id);
    boolean existsBySlugAndIdNot(String slug, Long id);

}
