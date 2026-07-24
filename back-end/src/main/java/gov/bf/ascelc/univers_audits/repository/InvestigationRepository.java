package gov.bf.ascelc.univers_audits.repository;

import gov.bf.ascelc.univers_audits.enums.InvestigationStatus;
import gov.bf.ascelc.univers_audits.model.entity.Investigation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface InvestigationRepository
        extends JpaRepository<Investigation, UUID> {

    @Query("""
            SELECT DISTINCT i FROM Investigation i
            LEFT JOIN FETCH i.members m
            LEFT JOIN FETCH m.agent
            WHERE i.id = :id
            """)
    Optional<Investigation> findById(@Param("id") UUID id);

    @Query("""
            SELECT DISTINCT i FROM Investigation i
            LEFT JOIN FETCH i.members m
            LEFT JOIN FETCH m.agent
            WHERE i.dossier.id = :dossierId
            """)
    Optional<Investigation> findByDossierId(
            @Param("dossierId") UUID dossierId);

    boolean existsByDossierId(UUID dossierId);

    // Combiner JOIN FETCH sur une collection avec Pageable force Hibernate à
    // charger TOUTE la table en mémoire pour paginer manuellement (voir
    // findAllWithMembers ci-dessus). Pour paginer correctement au niveau SQL,
    // on récupère d'abord la page d'IDs (JpaRepository.findAll(Pageable), sans
    // fetch join), puis on charge les membres uniquement pour ces IDs.
    @Query("""
            SELECT DISTINCT i FROM Investigation i
            LEFT JOIN FETCH i.members m
            LEFT JOIN FETCH m.agent
            WHERE i.id IN :ids
            """)
    List<Investigation> findAllWithMembersByIdIn(@Param("ids") List<UUID> ids);

    // ── Rapport — filtre par période de démarrage ─────────────
    Page<Investigation> findByStartDateBetween(
            Instant start, Instant end, Pageable pageable);

    @Query("""
            SELECT DISTINCT i FROM Investigation i
            LEFT JOIN FETCH i.members m
            LEFT JOIN FETCH m.agent
            WHERE i.status = :status
            """)
    List<Investigation> findByStatus(
            @Param("status") InvestigationStatus status);

    @Query("""
            SELECT DISTINCT i FROM Investigation i
            LEFT JOIN FETCH i.members m
            LEFT JOIN FETCH m.agent
            WHERE i.status = 'IN_PROGRESS'
            AND (
                (i.extendedDeadline IS NOT NULL
                 AND i.extendedDeadline < :now)
                OR
                (i.extendedDeadline IS NULL
                 AND i.plannedEndDate IS NOT NULL
                 AND i.plannedEndDate < :now)
            )
            ORDER BY i.plannedEndDate ASC
            """)
    List<Investigation> findOverdue(@Param("now") Instant now);

    @Query("""
            SELECT i.status, COUNT(i)
            FROM Investigation i
            GROUP BY i.status
            """)
    List<Object[]> countByStatus();

    @Query("""
            SELECT COUNT(i)
            FROM Investigation i
            WHERE i.status = 'IN_PROGRESS'
            AND (
                (i.extendedDeadline IS NOT NULL
                 AND i.extendedDeadline < :now)
                OR
                (i.extendedDeadline IS NULL
                 AND i.plannedEndDate < :now)
            )
            """)
    long countOverdue(@Param("now") Instant now);

    @Query(
            value = """
                SELECT AVG(
                    EXTRACT(EPOCH FROM (i.actual_end_date - i.start_date))
                    / 86400.0
                )
                FROM investigation i
                WHERE i.actual_end_date IS NOT NULL
                  AND i.start_date      IS NOT NULL
                  AND i.start_date >= :start
                  AND i.start_date <  :end
                """,
            nativeQuery = true
    )
    Double avgDurationInDays(
            @Param("start") Instant start,
            @Param("end")   Instant end);

    @Query(
            value = """
                SELECT AVG(
                    EXTRACT(EPOCH FROM (i.dei_approved_at - i.report_submitted_at))
                    / 86400.0
                )
                FROM investigation i
                WHERE i.dei_approved_at     IS NOT NULL
                  AND i.report_submitted_at IS NOT NULL
                  AND i.report_submitted_at >= :start
                  AND i.report_submitted_at <  :end
                """,
            nativeQuery = true
    )
    Double avgDeiApprovalDays(
            @Param("start") Instant start,
            @Param("end")   Instant end);

    @Query(
            value = """
                SELECT AVG(
                    EXTRACT(EPOCH FROM (i.cge_approved_at - i.dei_approved_at))
                    / 86400.0
                )
                FROM investigation i
                WHERE i.cge_approved_at IS NOT NULL
                  AND i.dei_approved_at IS NOT NULL
                  AND i.dei_approved_at >= :start
                  AND i.dei_approved_at <  :end
                """,
            nativeQuery = true
    )
    Double avgCgeApprovalDays(
            @Param("start") Instant start,
            @Param("end")   Instant end);

    @Query(
            value = """
                SELECT COUNT(*)
                FROM investigation i
                JOIN dossier d ON d.id = i.case_id
                WHERE d.reception_date >= :start
                  AND d.reception_date <  :end
                """,
            nativeQuery = true
    )
    long countByDossierReceptionDateBetween(
            @Param("start") Instant start,
            @Param("end")   Instant end);

    @Query(
            value = """
                SELECT COUNT(*)
                FROM investigation i
                JOIN dossier d ON d.id = i.case_id
                WHERE i.outcome = :outcome
                  AND d.reception_date >= :start
                  AND d.reception_date <  :end
                """,
            nativeQuery = true
    )
    long countByOutcomeBetween(
            @Param("outcome") String outcome,
            @Param("start")   Instant start,
            @Param("end")     Instant end);
}