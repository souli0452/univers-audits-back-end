package gov.bf.ascelc.univers_audits.model.entity;

import gov.bf.ascelc.univers_audits.abstracts.AuditEntity;
import gov.bf.ascelc.univers_audits.enums.NotificationChannel;
import gov.bf.ascelc.univers_audits.enums.NotificationStatus;
import gov.bf.ascelc.univers_audits.enums.NotificationType;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.time.Instant;

@Getter
@Setter
@Entity
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "notification", indexes = {
        // Toutes les notifications d'un dossier
        @Index(name = "idx_notification_case",
                columnList = "case_id"),
        // Notifications en attente d'envoi (tâche planifiée)
        @Index(name = "idx_notification_status",
                columnList = "status"),
        // Alertes de dépassement de délai
        @Index(name = "idx_notification_scheduled",
                columnList = "scheduled_at")
})
public class Notification extends AuditEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "case_id", nullable = false)
    private Dossier dossier;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 35)
    private NotificationType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false, length = 15)
    private NotificationChannel channel;

    @Column(name = "recipient", length = 200)
    private String recipient;

    @Column(name = "subject", length = 500)
    private String subject;

    @Column(name = "content", columnDefinition = "TEXT")
    private String content;

    @Column(name = "form_reference", length = 5)
    private String formReference;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 15)
    @Builder.Default
    private NotificationStatus status = NotificationStatus.PENDING;

    @Column(name = "scheduled_at")
    private Instant scheduledAt;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;


    @Column(name = "retry_count", nullable = false)
    @Builder.Default
    private Integer retryCount = 0;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "signed_by_id")
    private Agent signedBy;


    public void markAsSent() {
        this.status = NotificationStatus.SENT;
        this.sentAt = Instant.now();
        this.errorMessage = null;
    }


    public void markAsFailed(String errorMessage) {
        this.status = NotificationStatus.FAILED;
        this.errorMessage = errorMessage;
        this.retryCount++;
    }


    public boolean isOverdue() {
        return scheduledAt != null
                && Instant.now().isAfter(scheduledAt)
                && NotificationStatus.PENDING.equals(status);
    }


    public boolean canRetry() {
        return NotificationStatus.FAILED.equals(status)
                && retryCount < 3;
    }
}