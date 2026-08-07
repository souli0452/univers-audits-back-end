package gov.bf.ascelc.univers_audits.model.entity;

import gov.bf.ascelc.univers_audits.abstracts.AuditEntity;
import gov.bf.ascelc.univers_audits.enums.VisiteStatus;
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
@Table(name = "visite_terrain", indexes = {
        @Index(name = "idx_visite_terrain_investigation",
                columnList = "investigation_id")
})
public class VisiteTerrain extends AuditEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "investigation_id", nullable = false)
    private Investigation investigation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "conducted_by_id", nullable = false)
    private Agent plannedBy;

    @Column(name = "location", length = 300, nullable = false)
    private String location;

    @Column(name = "scheduled_at", nullable = false)
    private Instant scheduledAt;

    @Column(name = "conducted_at")
    private Instant conductedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private VisiteStatus status = VisiteStatus.SCHEDULED;

    @Column(name = "summary", columnDefinition = "TEXT")
    private String summary;

    @Column(name = "cancellation_reason", columnDefinition = "TEXT")
    private String cancellationReason;

    @Column(name = "carence_reason", columnDefinition = "TEXT")
    private String carenceReason;

    public void conduct(String summary) {
        this.conductedAt = Instant.now();
        this.summary = summary;
        this.status = VisiteStatus.CONDUCTED;
    }

    public void cancel(String reason) {
        this.cancellationReason = reason;
        this.status = VisiteStatus.CANCELLED;
    }

    public void markCarence(String reason) {
        this.carenceReason = reason;
        this.status = VisiteStatus.CARENCE;
    }
}
