package gov.bf.ascelc.univers_audits.model.entity;

import gov.bf.ascelc.univers_audits.abstracts.AuditEntity;
import gov.bf.ascelc.univers_audits.enums.RecommandationCtadp;
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
@Table(name = "decision_cge", indexes = {
        @Index(name = "idx_decision_cge_dossier",
                columnList = "dossier_id", unique = true)
})
public class DecisionCGE extends AuditEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dossier_id", nullable = false, unique = true)
    private Dossier dossier;

    @Enumerated(EnumType.STRING)
    @Column(name = "decision", nullable = false, length = 40)
    private RecommandationCtadp decision;

    @Column(name = "motif", length = 2000)
    private String motif;

    @Column(name = "date_decision", nullable = false)
    private Instant dateDecision;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agent_cge_id", nullable = false)
    private Agent agentCGE;
}
