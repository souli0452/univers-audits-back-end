package gov.bf.ascelc.univers_audits.model.entity;

import gov.bf.ascelc.univers_audits.abstracts.AuditEntity;
import jakarta.persistence.*;
        import lombok.*;
        import lombok.experimental.SuperBuilder;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Entity
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "departement", indexes = {
        @Index(name = "idx_departement_code",
                columnList = "code", unique = true)
})
public class Departement extends AuditEntity {

    @Column(name = "code", nullable = false,
            unique = true, length = 20)
    private String code;

    @Column(name = "libelle", nullable = false, length = 200)
    private String libelle;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "actif", nullable = false)
    @Builder.Default
    private Boolean actif = true;

    @Column(name = "ordre_affichage")
    @Builder.Default
    private Integer ordreAffichage = 0;

    // Liste des agents rattachés à ce département.
    @OneToMany(mappedBy = "departement",
            fetch = FetchType.LAZY)
    @Builder.Default
    private List<Agent> agents = new ArrayList<>();
}