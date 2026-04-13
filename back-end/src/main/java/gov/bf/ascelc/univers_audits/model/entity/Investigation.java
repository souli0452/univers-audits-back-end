package gov.bf.ascelc.univers_audits.model.entity;

import gov.bf.ascelc.univers_audits.abstracts.AuditEntity;
import gov.bf.ascelc.univers_audits.enums.InvestigationStatus;
import gov.bf.ascelc.univers_audits.enums.InvestigationOutcome;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Entity
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "investigation", indexes = {
        // Un dossier recevable → au plus une investigation
        @Index(name = "idx_investigation_case",
                columnList = "case_id", unique = true),
        // Filtrage par statut dans les tableaux de bord
        @Index(name = "idx_investigation_status",
                columnList = "status")
})
public class Investigation extends AuditEntity {

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "case_id", nullable = false, unique = true)
    private Dossier dossier;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cgea_id", nullable = false)
    private Agent cgea;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private InvestigationStatus status = InvestigationStatus.INITIATED;


    @Column(name = "start_date")
    private Instant startDate;

    @Column(name = "planned_duration_days", nullable = false)
    @Builder.Default
    private Integer plannedDurationDays = 90;

    @Column(name = "planned_end_date")
    private Instant plannedEndDate;

    @Column(name = "actual_end_date")
    private Instant actualEndDate;

    @Column(name = "extended_deadline")
    private Instant extendedDeadline;


    @Column(name = "extension_reason", columnDefinition = "TEXT")
    private String extensionReason;


    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "extension_approved_by_id")
    private Agent extensionApprovedBy;


    @Column(name = "suspension_reason", columnDefinition = "TEXT")
    private String suspensionReason;

    @Column(name = "final_report", columnDefinition = "TEXT")
    private String finalReport;

    @Column(name = "conclusions", columnDefinition = "TEXT")
    private String conclusions;


    @Column(name = "recommendations", columnDefinition = "TEXT")
    private String recommendations;

    @Enumerated(EnumType.STRING)
    @Column(name = "outcome", length = 30)
    private InvestigationOutcome outcome;

    @Column(name = "report_submitted_at")
    private Instant reportSubmittedAt;

    @Column(name = "dei_approved_at")
    private Instant deiApprovedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dei_approved_by_id")
    private Agent deiApprovedBy;


    @Column(name = "legal_advisor_approved_at")
    private Instant legalAdvisorApprovedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "legal_advisor_approved_by_id")
    private Agent legalAdvisorApprovedBy;


    @Column(name = "cge_approved_at")
    private Instant cgeApprovedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cge_approved_by_id")
    private Agent cgeApprovedBy;

    @OneToMany(mappedBy = "investigation",
            cascade = CascadeType.ALL,
            orphanRemoval = true)
    @Builder.Default
    private List<InvestigationMember> members = new ArrayList<>();


    @OneToMany(mappedBy = "investigation",
            fetch = FetchType.LAZY)
    @Builder.Default
    private List<Attachment> collectedEvidence = new ArrayList<>();


    public void start() {
        this.startDate = Instant.now();
        this.plannedEndDate = startDate
                .plusSeconds((long) plannedDurationDays * 24 * 3600);
        this.status = InvestigationStatus.IN_PROGRESS;
    }

    public void suspend(String reason) {
        this.suspensionReason = reason;
        this.status = InvestigationStatus.SUSPENDED;
    }

    public void extendDeadline(Instant newDeadline,
                               String reason,
                               Agent approvedBy) {
        this.extendedDeadline = newDeadline;
        this.extensionReason = reason;
        this.extensionApprovedBy = approvedBy;
    }


    public void complete() {
        this.actualEndDate = Instant.now();
        this.reportSubmittedAt = Instant.now();
        this.status = InvestigationStatus.COMPLETED;
    }


    public boolean isOverdue() {
        Instant deadline = extendedDeadline != null
                ? extendedDeadline
                : plannedEndDate;
        return deadline != null
                && Instant.now().isAfter(deadline)
                && InvestigationStatus.IN_PROGRESS.equals(status);
    }

    public long getRemainingDays() {
        Instant deadline = extendedDeadline != null
                ? extendedDeadline
                : plannedEndDate;
        if (deadline == null) return 0;
        long seconds = deadline.getEpochSecond()
                - Instant.now().getEpochSecond();
        return seconds / 86400;
    }
}