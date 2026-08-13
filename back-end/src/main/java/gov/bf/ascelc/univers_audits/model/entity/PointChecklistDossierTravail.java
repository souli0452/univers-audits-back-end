package gov.bf.ascelc.univers_audits.model.entity;

import gov.bf.ascelc.univers_audits.abstracts.AuditEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

@Getter
@Setter
@Entity
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "point_checklist_dossier_travail", indexes = {
        @Index(name = "idx_point_checklist_code",
                columnList = "code", unique = true)
})
public class PointChecklistDossierTravail extends AuditEntity {

    @Column(name = "code", nullable = false, unique = true, length = 50)
    private String code;

    @Column(name = "libelle", nullable = false, length = 500)
    private String libelle;

    @Column(name = "categorie", length = 100)
    private String categorie;

    @Column(name = "ordre", nullable = false)
    private Integer ordre;

    @Column(name = "actif", nullable = false)
    @Builder.Default
    private Boolean actif = true;
}
