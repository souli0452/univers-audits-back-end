package gov.bf.ascelc.univers_audits.repository;

import gov.bf.ascelc.univers_audits.enums.InvestigationStatus;
import gov.bf.ascelc.univers_audits.model.entity.Investigation;
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

    Optional<Investigation> findByDossierId(UUID dossierId);

    boolean existsByDossierId(UUID dossierId);

    List<Investigation> findByStatus(InvestigationStatus status);

    @Query("""
            SELECT i FROM Investigation i
            WHERE i.status = 'IN_PROGRESS'
            AND (
                (i.extendedDeadline IS NOT NULL
                 AND i.extendedDeadline < :now)
                OR
                (i.extendedDeadline IS NULL
                 AND i.plannedEndDate < :now)
            )
            ORDER BY i.plannedEndDate ASC
            """)
    List<Investigation> findOverdue(@Param("now") Instant now);

    @Query("""
        SELECT AVG((i.actualEndDate - i.startDate) by day)
        FROM Investigation i
        WHERE i.status = 'COMPLETED'
        AND i.startDate BETWEEN :start AND :end
        """)
    Double avgDurationInDays(
            @Param("start") Instant start,
            @Param("end") Instant end);

    @Query("""
            SELECT i.status, COUNT(i)
            FROM Investigation i
            GROUP BY i.status
            """)
    List<Object[]> countByStatus();
}