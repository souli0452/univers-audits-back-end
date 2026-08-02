package gov.bf.ascelc.univers_audits.model.entity;

import gov.bf.ascelc.univers_audits.abstracts.AuditEntity;
import gov.bf.ascelc.univers_audits.enums.RecommandationCtadp;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

@Getter
@Setter
@Entity
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "seance_ctadp_dossier", indexes = {
        @Index(name = "idx_seance_ctadp_dossier_seance",
                columnList = "seance_ctadp_id"),
        @Index(name = "idx_seance_ctadp_dossier_dossier",
                columnList = "dossier_id"),
        @Index(name = "idx_seance_ctadp_dossier_unique",
                columnList = "seance_ctadp_id, dossier_id", unique = true)
})
public class SeanceCtadpDossier extends AuditEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seance_ctadp_id", nullable = false)
    private SeanceCTADP seanceCtadp;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dossier_id", nullable = false)
    private Dossier dossier;

    @Enumerated(EnumType.STRING)
    @Column(name = "recommandation", length = 40)
    private RecommandationCtadp recommandation;

    @Column(name = "commentaire", length = 2000)
    private String commentaire;
}
