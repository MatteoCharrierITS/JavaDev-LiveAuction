package it.esercitazione.liveauction.producer.asta.repos;

import it.esercitazione.liveauction.producer.prodotto.models.Prodotto;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/** Accesso al prodotto necessario alla programmazione delle aste. */
public interface ProdottoAstaRepository extends Repository<Prodotto, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Prodotto p where p.id = :id")
    Optional<Prodotto> trovaPerProgrammazioneConLock(@Param("id") long id);
}
