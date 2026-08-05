package gov.bf.ascelc.univers_audits.model.entity;

import gov.bf.ascelc.univers_audits.abstracts.AuditEntity;
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
@Table(name = "revision_plan", indexes = {
        @Index(name = "idx_revision_plan_plan_investigation",
                columnList = "plan_investigation_id")
})
public class RevisionPlan extends AuditEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plan_investigation_id", nullable = false)
    private PlanInvestigation planInvestigation;

    @Column(name = "version_number", nullable = false)
    private Integer versionNumber;

    @Column(name = "objectifs", nullable = false, columnDefinition = "TEXT")
    private String objectifs;

    @Column(name = "methodologie", nullable = false, columnDefinition = "TEXT")
    private String methodologie;

    @Column(name = "moyens_mobilises", columnDefinition = "TEXT")
    private String moyensMobilises;

    @Column(name = "planning_procedures", columnDefinition = "TEXT")
    private String planningProcedures;

    @Column(name = "revised_at", nullable = false)
    private Instant revisedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "revised_by_id", nullable = false)
    private Agent revisedBy;

    @Column(name = "motif_revision", nullable = false, columnDefinition = "TEXT")
    private String motifRevision;
}
