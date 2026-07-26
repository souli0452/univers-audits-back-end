package gov.bf.ascelc.univers_audits.model.entity;

import gov.bf.ascelc.univers_audits.abstracts.AuditEntity;
import gov.bf.ascelc.univers_audits.enums.EscalationLevel;
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
@Table(name = "demande_documents", indexes = {
        // Toutes les demandes de documents d'une investigation
        @Index(name = "idx_demande_documents_investigation",
                columnList = "investigation_id")
})
public class DemandeDocuments extends AuditEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "investigation_id", nullable = false)
    private Investigation investigation;

    @Column(name = "recipient_label", nullable = false, length = 300)
    private String recipientLabel;

    @Column(name = "documents_requested", nullable = false, columnDefinition = "TEXT")
    private String documentsRequested;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requested_by_id", nullable = false)
    private Agent requestedBy;

    @Column(name = "sent_at", nullable = false)
    private Instant sentAt;

    @Column(name = "deadline", nullable = false)
    private Instant deadline;

    @Enumerated(EnumType.STRING)
    @Column(name = "escalation_level", nullable = false, length = 20)
    @Builder.Default
    private EscalationLevel escalationLevel = EscalationLevel.INITIAL;

    @Column(name = "received", nullable = false)
    @Builder.Default
    private Boolean received = false;

    @Column(name = "received_at")
    private Instant receivedAt;

    public void markReceived() {
        this.received = true;
        this.receivedAt = Instant.now();
    }

    public void escalate(EscalationLevel newLevel, int deadlineDays) {
        this.escalationLevel = newLevel;
        this.sentAt = Instant.now();
        this.deadline = sentAt.plusSeconds(deadlineDays * 24L * 3600);
    }

    public boolean isOverdue() {
        return !Boolean.TRUE.equals(received)
                && deadline != null
                && Instant.now().isAfter(deadline);
    }

    public EscalationLevel nextEscalationLevel() {
        int nextOrdinal = escalationLevel.ordinal() + 1;
        EscalationLevel[] levels = EscalationLevel.values();
        return nextOrdinal < levels.length ? levels[nextOrdinal] : null;
    }
}
