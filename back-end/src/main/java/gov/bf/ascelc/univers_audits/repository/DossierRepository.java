package gov.bf.ascelc.univers_audits.repository;

import gov.bf.ascelc.univers_audits.enums.DossierStatus;
import gov.bf.ascelc.univers_audits.enums.SubmissionMode;
import gov.bf.ascelc.univers_audits.enums.TypeSaisine;
import gov.bf.ascelc.univers_audits.model.entity.Dossier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DossierRepository
        extends JpaRepository<Dossier, UUID>,
        JpaSpecificationExecutor<Dossier> {

    Optional<Dossier> findByAccessCode(String accessCode);

    Optional<Dossier> findByNumber(String number);

    boolean existsByAccessCode(String accessCode);

    boolean existsByNumber(String number);

    Page<Dossier> findByStatus(DossierStatus status, Pageable pageable);

    Page<Dossier> findByStatusIn(List<DossierStatus> statuses, Pageable pageable);

    Page<Dossier> findByAgentInChargeId(UUID agentId, Pageable pageable);

    Page<Dossier> findByAgentInChargeIdAndStatus(
            UUID agentId,
            DossierStatus status,
            Pageable pageable);

    List<Dossier> findByDeclarantId(UUID declarantId);

    @Query("""
            SELECT d FROM Dossier d
            WHERE d.acknowledgmentDeadline < :now
            AND d.status NOT IN (
                'CLOS', 'CLASSE', 'IRRECEVABLE', 'TRANSFERE'
            )
            ORDER BY d.acknowledgmentDeadline ASC
            """)
    List<Dossier> findOverdueAcknowledgments(@Param("now") Instant now);

    @Query("""
            SELECT d FROM Dossier d
            WHERE d.status = 'EN_ATTENTE_COMPLEMENT'
            AND d.additionalInfoDeadline < :now
            ORDER BY d.additionalInfoDeadline ASC
            """)
    List<Dossier> findOverdueComplementRequests(@Param("now") Instant now);

    @Query("""
            SELECT d.status, COUNT(d)
            FROM Dossier d
            GROUP BY d.status
            """)
    List<Object[]> countByStatus();

    @Query("""
            SELECT d.submissionMode, COUNT(d)
            FROM Dossier d
            WHERE d.receptionDate BETWEEN :start AND :end
            GROUP BY d.submissionMode
            """)
    List<Object[]> countBySubmissionModeBetween(
            @Param("start") Instant start,
            @Param("end") Instant end);

    @Query("""
            SELECT d.type, COUNT(d)
            FROM Dossier d
            WHERE d.receptionDate BETWEEN :start AND :end
            GROUP BY d.type
            """)
    List<Object[]> countByTypeBetween(
            @Param("start") Instant start,
            @Param("end") Instant end);

    @Query("""
            SELECT SUM(d.estimatedLoss)
            FROM Dossier d
            WHERE d.receptionDate BETWEEN :start AND :end
            AND d.estimatedLoss IS NOT NULL
            """)
    java.math.BigDecimal sumEstimatedLossBetween(
            @Param("start") Instant start,
            @Param("end") Instant end);

    @Query("""
            SELECT AVG((d.eligibilityDecisionDate - d.receptionDate) by second)
            FROM Dossier d
            WHERE d.receptionDate BETWEEN :start AND :end
            AND d.eligibilityDecisionDate IS NOT NULL
            """)
    Double avgProcessingTimeInSeconds(
            @Param("start") Instant start,
            @Param("end") Instant end);

    long countByReceptionDateBetween(Instant start, Instant end);

    long countByStatusAndReceptionDateBetween(
            DossierStatus status,
            Instant start,
            Instant end);
}