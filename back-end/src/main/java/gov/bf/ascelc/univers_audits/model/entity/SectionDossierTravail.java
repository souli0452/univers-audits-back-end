package gov.bf.ascelc.univers_audits.model.entity;

import gov.bf.ascelc.univers_audits.abstracts.AuditEntity;
import gov.bf.ascelc.univers_audits.enums.TypeSectionDossierTravail;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

@Getter
@Setter
@Entity
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "section_dossier_travail", indexes = {
        @Index(name = "idx_section_dossier", columnList = "dossier_id"),
        @Index(name = "idx_section_dossier_type", columnList = "dossier_id, type")
})
public class SectionDossierTravail extends AuditEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dossier_id", nullable = false)
    private Dossier dossier;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 35)
    private TypeSectionDossierTravail type;

    @Column(name = "libelle", length = 255)
    private String libelle;
}
