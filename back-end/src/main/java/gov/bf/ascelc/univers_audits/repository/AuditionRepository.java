package gov.bf.ascelc.univers_audits.repository;

import gov.bf.ascelc.univers_audits.model.entity.Audition;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AuditionRepository extends JpaRepository<Audition, UUID> {

    List<Audition> findByInvestigationIdOrderByScheduledAtAsc(UUID investigationId);

    @Query(value = """
            SELECT a FROM Audition a
            LEFT JOIN FETCH a.investigation inv
            LEFT JOIN FETCH inv.dossier d
            LEFT JOIN FETCH d.declarant
            LEFT JOIN FETCH a.witness
            LEFT JOIN FETCH a.targetedParty
            ORDER BY a.scheduledAt DESC
            """,
            countQuery = "SELECT COUNT(a) FROM Audition a")
    Page<Audition> findAllForRegistre(Pageable pageable);
}
