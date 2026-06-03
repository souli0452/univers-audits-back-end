package gov.bf.ascelc.univers_audits.repository;

import gov.bf.ascelc.univers_audits.enums.DossierStatus;
import gov.bf.ascelc.univers_audits.model.entity.Dossier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
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

    long countByStatusIn(List<DossierStatus> statuses);

    Page<Dossier> findByAgentInChargeId(UUID agentId, Pageable pageable);

    Page<Dossier> findByAgentInChargeIdAndStatus(
            UUID agentId, DossierStatus status, Pageable pageable);

    List<Dossier> findByDeclarantId(UUID declarantId);


    Page<Dossier> findByReceptionDateBetween(
            Instant start, Instant end, Pageable pageable);



    @Query("""
            SELECT d FROM Dossier d
            WHERE d.acknowledgmentDeadline < :now
            AND d.status NOT IN (
                gov.bf.ascelc.univers_audits.enums.DossierStatus.CLOS,
                gov.bf.ascelc.univers_audits.enums.DossierStatus.CLASSE,
                gov.bf.ascelc.univers_audits.enums.DossierStatus.IRRECEVABLE,
                gov.bf.ascelc.univers_audits.enums.DossierStatus.TRANSFERE
            )
            ORDER BY d.acknowledgmentDeadline ASC
            """)
    List<Dossier> findOverdueAcknowledgments(@Param("now") Instant now);

    @Query("""
            SELECT d FROM Dossier d
            WHERE d.status = gov.bf.ascelc.univers_audits.enums.DossierStatus.EN_ATTENTE_COMPLEMENT
            AND d.additionalInfoDeadline < :now
            ORDER BY d.additionalInfoDeadline ASC
            """)
    List<Dossier> findOverdueComplementRequests(@Param("now") Instant now);

    @Query("""
            SELECT d FROM Dossier d
            JOIN Investigation i ON i.dossier.id = d.id
            WHERE d.status = gov.bf.ascelc.univers_audits.enums.DossierStatus.EN_INVESTIGATION
            AND i.status IN (
                gov.bf.ascelc.univers_audits.enums.InvestigationStatus.INITIATED,
                gov.bf.ascelc.univers_audits.enums.InvestigationStatus.IN_PROGRESS
            )
            AND COALESCE(i.extendedDeadline, i.plannedEndDate) < :now
            ORDER BY i.plannedEndDate ASC
            """)
    List<Dossier> findOverdueInvestigations(@Param("now") Instant now);


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
            @Param("end")   Instant end);

    @Query("""
            SELECT d.type, COUNT(d)
            FROM Dossier d
            WHERE d.receptionDate BETWEEN :start AND :end
            GROUP BY d.type
            """)
    List<Object[]> countByTypeBetween(
            @Param("start") Instant start,
            @Param("end")   Instant end);

    @Query("""
            SELECT SUM(d.estimatedLoss)
            FROM Dossier d
            WHERE d.receptionDate BETWEEN :start AND :end
            AND d.estimatedLoss IS NOT NULL
            """)
    BigDecimal sumEstimatedLossBetween(
            @Param("start") Instant start,
            @Param("end")   Instant end);

    long countByReceptionDateBetween(Instant start, Instant end);

    long countByStatusAndReceptionDateBetween(
            DossierStatus status, Instant start, Instant end);

    @Query("""
            SELECT d.status, COUNT(d)
            FROM Dossier d
            WHERE d.receptionDate BETWEEN :start AND :end
            GROUP BY d.status
            """)
    List<Object[]> countByStatusAndReceptionDateBetween(
            @Param("start") Instant start,
            @Param("end")   Instant end);

    @Query("""
            SELECT COUNT(d)
            FROM Dossier d
            WHERE d.status IN :statuses
            AND d.receptionDate BETWEEN :start AND :end
            """)
    long countByStatusInAndReceptionDateBetween(
            @Param("statuses") List<DossierStatus> statuses,
            @Param("start")    Instant start,
            @Param("end")      Instant end);

    @Query(value = """
            SELECT AVG(
                EXTRACT(EPOCH FROM (d.reception_date - d.created_at))
                / 86400.0
            )
            FROM dossier d
            WHERE d.reception_date IS NOT NULL
              AND d.created_at     IS NOT NULL
              AND d.reception_date >= :start
              AND d.reception_date <  :end
            """, nativeQuery = true)
    Double avgRegistrationDelayInDays(
            @Param("start") Instant start,
            @Param("end")   Instant end);

    @Query(value = """
            SELECT AVG(
                EXTRACT(EPOCH FROM (eligibility_decision_date - reception_date))
            )
            FROM dossier
            WHERE reception_date    >= :start
              AND reception_date    <  :end
              AND eligibility_decision_date IS NOT NULL
            """, nativeQuery = true)
    Double avgProcessingTimeInSeconds(
            @Param("start") Instant start,
            @Param("end")   Instant end);
}