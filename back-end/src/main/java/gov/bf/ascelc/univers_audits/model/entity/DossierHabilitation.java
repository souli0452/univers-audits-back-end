package gov.bf.ascelc.univers_audits.model.entity;

import gov.bf.ascelc.univers_audits.abstracts.AuditEntity;
import gov.bf.ascelc.univers_audits.enums.HabilitationSource;
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
@Table(name = "dossier_habilitation", indexes = {
        @Index(name = "idx_habilitation_dossier_agent",
                columnList = "dossier_id, agent_id"),
        @Index(name = "idx_habilitation_agent",
                columnList = "agent_id")
})
public class DossierHabilitation extends AuditEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dossier_id", nullable = false)
    private Dossier dossier;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agent_id", nullable = false)
    private Agent agent;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 25)
    private HabilitationSource source;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "granted_by_id")
    private Agent grantedBy;

    @Column(name = "reason", length = 500)
    private String reason;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "revoked_by_id")
    private Agent revokedBy;

    @Column(name = "revocation_reason", length = 500)
    private String revocationReason;

    public boolean isActive() {
        return revokedAt == null;
    }

    public void revoke(Agent revokedBy, String revocationReason) {
        this.revokedAt = Instant.now();
        this.revokedBy = revokedBy;
        this.revocationReason = revocationReason;
    }
}
