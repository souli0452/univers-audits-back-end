package gov.bf.ascelc.univers_audits.repository;

import gov.bf.ascelc.univers_audits.enums.DossierStatus;
import gov.bf.ascelc.univers_audits.model.entity.StatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface StatusHistoryRepository
        extends JpaRepository<StatusHistory, UUID> {

    List<StatusHistory> findByDossierIdOrderByChangedAtAsc(
            UUID dossierId);

    Optional<StatusHistory> findFirstByDossierIdOrderByChangedAtDesc(
            UUID dossierId);

    List<StatusHistory> findByAgentIdOrderByChangedAtDesc(
            UUID agentId);

    @Query("""
            SELECT sh.newStatus, COUNT(sh)
            FROM StatusHistory sh
            WHERE sh.changedAt BETWEEN :start AND :end
            GROUP BY sh.newStatus
            """)
    List<Object[]> countTransitionsByStatus(
            @Param("start") java.time.Instant start,
            @Param("end") java.time.Instant end);
}