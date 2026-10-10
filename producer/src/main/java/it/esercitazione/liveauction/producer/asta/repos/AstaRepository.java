package it.esercitazione.liveauction.producer.asta.repos;

import it.esercitazione.liveauction.producer.asta.models.Asta;
import it.esercitazione.liveauction.producer.asta.models.Stato;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface AstaRepository extends JpaRepository<Asta, Long> {

    @Query("select a.id from Asta a where a.stato = :stato and a.inizioAt <= :limite order by a.inizioAt, a.id")
    List<Long> trovaIdDaAttivare(@Param("stato") Stato stato, @Param("limite") Instant limite);

    @Query("select a.id from Asta a where a.stato = :stato and a.fineAt <= :limite order by a.fineAt, a.id")
    List<Long> trovaIdDaChiudere(@Param("stato") Stato stato, @Param("limite") Instant limite);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Asta a where a.id = :id")
    Optional<Asta> trovaConLock(@Param("id") long id);
}
