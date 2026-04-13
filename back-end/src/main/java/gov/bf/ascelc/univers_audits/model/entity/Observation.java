package gov.bf.ascelc.univers_audits.model.entity;

import gov.bf.ascelc.univers_audits.abstracts.AuditEntity;
import gov.bf.ascelc.univers_audits.enums.DossierStatus;
import gov.bf.ascelc.univers_audits.enums.ObservationType;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

@Getter
@Setter
@Entity
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "observation", indexes = {
        // Recherche rapide de toutes les observations d'un dossier
        @Index(name = "idx_observation_case",
                columnList = "case_id"),
        // Tri chronologique optimisé
        @Index(name = "idx_observation_created_at",
                columnList = "created_at")
})
public class Observation extends AuditEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "case_id", nullable = false,
            updatable = false)
    private Dossier dossier;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 30,
            updatable = false)
    private ObservationType type;

    @Column(name = "content", nullable = false,
            columnDefinition = "TEXT", updatable = false)
    private String content;

    @Column(name = "confidential", nullable = false,
            updatable = false)
    @Builder.Default
    private Boolean confidential = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id", nullable = false,
            updatable = false)
    private Agent author;

    @Column(name = "author_full_name", nullable = false,
            length = 200, updatable = false)
    private String authorFullName;

    @Enumerated(EnumType.STRING)
    @Column(name = "status_snapshot", length = 35,
            updatable = false)
    private DossierStatus statusSnapshot;

    @Column(name = "cree_par", length = 100)
    private String creePar;
}