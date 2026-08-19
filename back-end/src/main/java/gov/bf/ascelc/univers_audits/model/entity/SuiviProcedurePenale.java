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
@Table(name = "suivi_procedure_penale", indexes = {
        @Index(name = "idx_suivi_procedure_penale_investigation",
                columnList = "investigation_id")
})
public class SuiviProcedurePenale extends AuditEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "investigation_id", nullable = false)
    private Investigation investigation;

    @Column(name = "phase_at", nullable = false)
    private Instant phaseAt;

    @Column(name = "phase", nullable = false, length = 300)
    private String phase;

    @Column(name = "commentaire", columnDefinition = "TEXT")
    private String commentaire;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agent_id", nullable = false)
    private Agent agent;

    @Column(name = "submitted_at", nullable = false)
    private Instant submittedAt;
}
