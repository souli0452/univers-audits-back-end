package gov.bf.ascelc.univers_audits.repository;

import gov.bf.ascelc.univers_audits.model.entity.DemandeDocuments;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface DemandeDocumentsRepository extends JpaRepository<DemandeDocuments, UUID> {

    List<DemandeDocuments> findByInvestigationIdOrderBySentAtDesc(UUID investigationId);

    @Query("""
            SELECT dd FROM DemandeDocuments dd
            JOIN FETCH dd.investigation i
            JOIN FETCH i.dossier
            WHERE dd.received = false
            AND dd.deadline < :now
            ORDER BY dd.deadline ASC
            """)
    List<DemandeDocuments> findOverdue(@Param("now") Instant now);

    @Query("""
            SELECT dd FROM DemandeDocuments dd
            JOIN FETCH dd.investigation i
            JOIN FETCH i.dossier
            WHERE dd.received = false
            AND dd.deadline BETWEEN :now AND :in3Days
            ORDER BY dd.deadline ASC
            """)
    List<DemandeDocuments> findDueWithin(
            @Param("now") Instant now, @Param("in3Days") Instant in3Days);

    @Query("""
            SELECT dd FROM DemandeDocuments dd
            JOIN FETCH dd.investigation i
            JOIN FETCH i.dossier
            WHERE dd.received = false
            AND dd.deadline < :graceThreshold
            ORDER BY dd.deadline ASC
            """)
    List<DemandeDocuments> findOverdueBeyondGrace(
            @Param("graceThreshold") Instant graceThreshold);
}
