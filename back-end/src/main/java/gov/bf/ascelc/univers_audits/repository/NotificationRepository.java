package gov.bf.ascelc.univers_audits.repository;

import gov.bf.ascelc.univers_audits.enums.NotificationStatus;
import gov.bf.ascelc.univers_audits.enums.NotificationType;
import gov.bf.ascelc.univers_audits.model.entity.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface NotificationRepository
        extends JpaRepository<Notification, UUID> {

    // ── Par dossier ───────────────────────────────────────────

    /** Notifications d'un dossier — paginées */
    Page<Notification> findByDossierId(UUID dossierId, Pageable pageable);

    /** Notifications d'un dossier — liste complète */
    List<Notification> findByDossierId(UUID dossierId);

    // ── Par agent en charge du dossier (pour /my) ─────────────

    Page<Notification> findByDossierAgentInChargeId(
            UUID agentId, Pageable pageable);

    Page<Notification> findByDossierAgentInChargeIdAndStatusIn(
            UUID agentId,
            List<NotificationStatus> statuses,
            Pageable pageable);

    // ── Par statut ────────────────────────────────────────────

    List<Notification> findByStatus(NotificationStatus status);

    Page<Notification> findByStatusIn(
            List<NotificationStatus> statuses, Pageable pageable);

    // ── Scheduler ─────────────────────────────────────────────

    @Query("""
            SELECT n FROM Notification n
            WHERE n.status = 'PENDING'
            AND n.scheduledAt < :now
            ORDER BY n.scheduledAt ASC
            """)
    List<Notification> findOverdue(@Param("now") Instant now);

    @Query("""
            SELECT n FROM Notification n
            WHERE n.status = 'FAILED'
            AND n.retryCount < 3
            ORDER BY n.createdAt ASC
            """)
    List<Notification> findRetryable();

    // ── Divers ────────────────────────────────────────────────

    boolean existsByDossierIdAndType(UUID dossierId, NotificationType type);
}