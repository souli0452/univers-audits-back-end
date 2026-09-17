package gov.bf.ascelc.univers_audits.repository;

import gov.bf.ascelc.univers_audits.model.entity.JourFerie;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface JourFerieRepository
        extends JpaRepository<JourFerie, UUID> {

    boolean existsByDateAndActifTrue(LocalDate date);

    boolean existsByDate(LocalDate date);

    List<JourFerie> findByActifTrueOrderByDateAsc();
}
