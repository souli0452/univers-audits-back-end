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
@Table(name = "checklist_dossier_travail_coche", indexes = {
        @Index(name = "idx_checklist_coche_investigation",
                columnList = "investigation_id"),
        @Index(name = "idx_checklist_coche_unique",
                columnList = "investigation_id, point_id", unique = true)
})
public class ChecklistDossierTravailCoche extends AuditEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "investigation_id", nullable = false)
    private Investigation investigation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "point_id", nullable = false)
    private PointChecklistDossierTravail point;

    @Column(name = "coche", nullable = false)
    @Builder.Default
    private Boolean coche = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "coche_par_id")
    private Agent cochePar;

    @Column(name = "coche_at")
    private Instant cocheAt;

    @Column(name = "commentaire", length = 2000)
    private String commentaire;
}
