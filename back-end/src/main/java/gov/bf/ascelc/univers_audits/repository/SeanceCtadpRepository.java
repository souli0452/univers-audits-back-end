package gov.bf.ascelc.univers_audits.repository;

import gov.bf.ascelc.univers_audits.model.entity.SeanceCTADP;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface SeanceCtadpRepository extends JpaRepository<SeanceCTADP, UUID> {

    @Query("""
            SELECT DISTINCT s FROM SeanceCTADP s
            LEFT JOIN FETCH s.dossiers sd
            LEFT JOIN FETCH sd.dossier
            WHERE s.id = :id
            """)
    Optional<SeanceCTADP> findById(@Param("id") UUID id);
}
