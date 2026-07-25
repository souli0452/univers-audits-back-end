package gov.bf.ascelc.univers_audits.model.entity;

import gov.bf.ascelc.univers_audits.abstracts.AuditEntity;
import gov.bf.ascelc.univers_audits.enums.AuditionStatus;
import gov.bf.ascelc.univers_audits.enums.IntervieweeType;
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
@Table(name = "audition", indexes = {
        // Toutes les auditions d'une investigation
        @Index(name = "idx_audition_investigation",
                columnList = "investigation_id")
})
public class Audition extends AuditEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "investigation_id", nullable = false)
    private Investigation investigation;

    @Enumerated(EnumType.STRING)
    @Column(name = "interviewee_type", nullable = false, length = 20)
    private IntervieweeType intervieweeType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "targeted_party_id")
    private TargetedParty targetedParty;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "witness_id")
    private Witness witness;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "conducted_by_id", nullable = false)
    private Agent conductedBy;

    @Column(name = "location", length = 300)
    private String location;

    @Column(name = "scheduled_at", nullable = false)
    private Instant scheduledAt;

    @Column(name = "conducted_at")
    private Instant conductedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private AuditionStatus status = AuditionStatus.SCHEDULED;

    @Column(name = "summary", columnDefinition = "TEXT")
    private String summary;

    @Column(name = "cancellation_reason", columnDefinition = "TEXT")
    private String cancellationReason;

    public void conduct(String summary) {
        this.conductedAt = Instant.now();
        this.summary = summary;
        this.status = AuditionStatus.CONDUCTED;
    }

    public void cancel(String reason) {
        this.cancellationReason = reason;
        this.status = AuditionStatus.CANCELLED;
    }

    public String getIntervieweeDisplayName() {
        if (IntervieweeType.WITNESS.equals(intervieweeType) && witness != null) {
            return witness.getDisplayName();
        }
        if (IntervieweeType.TARGETED_PARTY.equals(intervieweeType) && targetedParty != null) {
            return targetedParty.getDisplayName();
        }
        return "Inconnu";
    }
}
