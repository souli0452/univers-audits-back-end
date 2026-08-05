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
@Table(name = "plan_investigation")
public class PlanInvestigation extends AuditEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "investigation_id", nullable = false, unique = true)
    private Investigation investigation;

    @Column(name = "objectifs", nullable = false, columnDefinition = "TEXT")
    private String objectifs;

    @Column(name = "methodologie", nullable = false, columnDefinition = "TEXT")
    private String methodologie;

    @Column(name = "moyens_mobilises", columnDefinition = "TEXT")
    private String moyensMobilises;

    @Column(name = "planning_procedures", columnDefinition = "TEXT")
    private String planningProcedures;

    @Column(name = "plan_version", nullable = false)
    private Integer planVersion;

    @Column(name = "submitted_at", nullable = false)
    private Instant submittedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "submitted_by_id", nullable = false)
    private Agent submittedBy;

    @Column(name = "validated_at")
    private Instant validatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "validated_by_id")
    private Agent validatedBy;
}
