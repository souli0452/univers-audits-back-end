package gov.bf.ascelc.univers_audits.model.entity;

import gov.bf.ascelc.univers_audits.abstracts.AuditEntity;
import gov.bf.ascelc.univers_audits.enums.StatutProcedureUrgence;
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
@Table(name = "procedure_urgence", indexes = {
        @Index(name = "idx_procedure_urgence_investigation",
                columnList = "investigation_id")
})
public class ProcedureUrgence extends AuditEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "investigation_id", nullable = false)
    private Investigation investigation;

    @Column(name = "justification", nullable = false, columnDefinition = "TEXT")
    private String justification;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requested_by_id", nullable = false)
    private Agent requestedBy;

    @Column(name = "requested_at", nullable = false)
    private Instant requestedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private StatutProcedureUrgence status = StatutProcedureUrgence.EN_ATTENTE;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "decided_by_id")
    private Agent decidedBy;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "motif_decision", columnDefinition = "TEXT")
    private String motifDecision;
}
