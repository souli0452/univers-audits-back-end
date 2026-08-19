package gov.bf.ascelc.univers_audits.repository;

import gov.bf.ascelc.univers_audits.model.entity.DecisionCGE;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DecisionCGERepository extends JpaRepository<DecisionCGE, UUID> {

    Optional<DecisionCGE> findByDossierId(UUID dossierId);

    @Query("""
            SELECT d.decision, COUNT(d)
            FROM DecisionCGE d
            WHERE d.dossier.receptionDate BETWEEN :start AND :end
            GROUP BY d.decision
            """)
    List<Object[]> countByDecisionBetween(
            @Param("start") Instant start,
            @Param("end")   Instant end);
}
