package gov.bf.ascelc.univers_audits.model.entity;

import gov.bf.ascelc.univers_audits.abstracts.AuditEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Setter
@Entity
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "constitution_partie_civile")
public class ConstitutionPartieCivile extends AuditEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "investigation_id", nullable = false, unique = true)
    private Investigation investigation;

    @Column(name = "constitue_at", nullable = false)
    private Instant constitueAt;

    @Column(name = "montant_reclame", precision = 15, scale = 2)
    private BigDecimal montantReclame;

    @Column(name = "justification", nullable = false, columnDefinition = "TEXT")
    private String justification;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "constituee_par_id", nullable = false)
    private Agent constitueePar;

    @Column(name = "submitted_at", nullable = false)
    private Instant submittedAt;
}
