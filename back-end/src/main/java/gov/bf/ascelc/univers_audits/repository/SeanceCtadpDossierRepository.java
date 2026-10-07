package gov.bf.ascelc.univers_audits.repository;

import gov.bf.ascelc.univers_audits.model.entity.SeanceCtadpDossier;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SeanceCtadpDossierRepository
        extends JpaRepository<SeanceCtadpDossier, UUID> {

    boolean existsBySeanceCtadpIdAndDossierId(UUID seanceCtadpId, UUID dossierId);

    List<SeanceCtadpDossier> findByDossierIdOrderByCreatedAtAsc(UUID dossierId);

    Optional<SeanceCtadpDossier> findBySeanceCtadpIdAndDossierId(
            UUID seanceCtadpId, UUID dossierId);

    @Query("""
            SELECT s.recommandation, COUNT(s)
            FROM SeanceCtadpDossier s
            WHERE s.dossier.receptionDate BETWEEN :start AND :end
            GROUP BY s.recommandation
            """)
    List<Object[]> countByRecommandationBetween(
            @Param("start") Instant start,
            @Param("end")   Instant end);
}
