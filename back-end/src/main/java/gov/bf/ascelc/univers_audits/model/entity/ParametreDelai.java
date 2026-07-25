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
@Table(name = "parametre_delai", indexes = {
        @Index(name = "idx_parametre_delai_code",
                columnList = "code", unique = true)
})
public class ParametreDelai extends AuditEntity {

    @Column(name = "code", nullable = false, unique = true, length = 50)
    private String code;

    @Column(name = "libelle", nullable = false, length = 300)
    private String libelle;

    @Column(name = "valeur_jours")
    private Integer valeurJours;

    @Column(name = "jours_ouvrables", nullable = false)
    @Builder.Default
    private Boolean joursOuvrables = true;

    @Column(name = "actif", nullable = false)
    @Builder.Default
    private Boolean actif = true;
}
