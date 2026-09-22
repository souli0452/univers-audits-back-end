package gov.bf.ascelc.univers_audits.repository;

import gov.bf.ascelc.univers_audits.enums.NotificationStatus;
import gov.bf.ascelc.univers_audits.enums.NotificationType;
import gov.bf.ascelc.univers_audits.model.entity.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    Page<Notification> findByDossierId(UUID dossierId, Pageable pageable);
    List<Notification> findByDossierId(UUID dossierId);

    Page<Notification> findByDossierAgentInChargeId(
            UUID agentId, Pageable pageable);

    Page<Notification> findByDossierAgentInChargeIdAndStatusIn(
            UUID agentId, List<NotificationStatus> statuses, Pageable pageable);

    Page<Notification> findByDossierAgentInChargeIdAndReadAtIsNull(
            UUID agentId, Pageable pageable);

    long countByDossierAgentInChargeIdAndReadAtIsNull(UUID agentId);

    @Modifying
    @Query("""
            UPDATE Notification n
            SET n.readAt = :now
            WHERE n.dossier.agentInCharge.id = :agentId
              AND n.readAt IS NULL
            """)
    int markAllReadByAgent(@Param("agentId") UUID agentId,
                           @Param("now")     Instant now);

    @Query("""
            SELECT n FROM Notification n
            WHERE n.dossier.agentInCharge.id = :agentId
               OR n.recipient = :keycloakId
            ORDER BY n.createdAt DESC
            """)
    Page<Notification> findByAgentOrRecipient(
            @Param("agentId")    UUID   agentId,
            @Param("keycloakId") String keycloakId,
            Pageable pageable);


    @Query("""
            SELECT n FROM Notification n
            WHERE (n.dossier.agentInCharge.id = :agentId
                OR n.recipient = :keycloakId)
              AND n.readAt IS NULL
            ORDER BY n.createdAt DESC
            """)
    Page<Notification> findUnreadByAgentOrRecipient(
            @Param("agentId")    UUID   agentId,
            @Param("keycloakId") String keycloakId,
            Pageable pageable);

    @Query("""
            SELECT COUNT(n) FROM Notification n
            WHERE (n.dossier.agentInCharge.id = :agentId
                OR n.recipient = :keycloakId)
              AND n.readAt IS NULL
            """)
    long countUnreadByAgentOrRecipient(
            @Param("agentId")    UUID   agentId,
            @Param("keycloakId") String keycloakId);

    @Modifying
    @Query("""
            UPDATE Notification n
            SET n.readAt = :now
            WHERE (n.dossier.agentInCharge.id = :agentId
                OR n.recipient = :keycloakId)
              AND n.readAt IS NULL
            """)
    int markAllReadByAgentOrRecipient(
            @Param("agentId")    UUID    agentId,
            @Param("keycloakId") String  keycloakId,
            @Param("now")        Instant now);

    List<Notification> findByStatus(NotificationStatus status);

    Page<Notification> findByStatusIn(
            List<NotificationStatus> statuses, Pageable pageable);

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

    boolean existsByDossierIdAndType(UUID dossierId, NotificationType type);

    boolean existsByDemandeDocumentsIdAndType(UUID demandeDocumentsId, NotificationType type);

    // Une DemandeDocuments passe par plusieurs cycles d'escalade sur la même
    // ligne (INITIAL -> RELANCE -> SOMMATION -> SAISINE_JUDICIAIRE), chaque
    // escalade réarmant sentAt/deadline. Une alerte créée avant le sentAt
    // courant appartient à un cycle précédent et ne doit pas supprimer
    // l'alerte du cycle en cours — voir NotificationServiceImpl.
    boolean existsByDemandeDocumentsIdAndTypeAndCreatedAtAfter(
            UUID demandeDocumentsId, NotificationType type, Instant after);

    @Query("""
            SELECT COUNT(n)
            FROM Notification n
            WHERE n.type = :type
              AND n.status = :status
              AND n.dossier.receptionDate BETWEEN :start AND :end
            """)
    long countByTypeAndStatusAndDossierReceptionDateBetween(
            @Param("type")   NotificationType type,
            @Param("status") NotificationStatus status,
            @Param("start")  Instant start,
            @Param("end")    Instant end);
}