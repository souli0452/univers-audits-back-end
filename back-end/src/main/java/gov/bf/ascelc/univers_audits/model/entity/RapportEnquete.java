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
@Table(name = "rapport_enquete")
public class RapportEnquete extends AuditEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "investigation_id", nullable = false, unique = true)
    private Investigation investigation;

    @Column(name = "titre", columnDefinition = "TEXT")
    private String titre;

    @Column(name = "introduction", columnDefinition = "TEXT")
    private String introduction;

    @Column(name = "methodologie", columnDefinition = "TEXT")
    private String methodologie;

    @Column(name = "informations_collectees", columnDefinition = "TEXT")
    private String informationsCollectees;

    @Column(name = "expose_factuel_anomalies", columnDefinition = "TEXT")
    private String exposeFactuelAnomalies;

    @Column(name = "quantification_prejudice", columnDefinition = "TEXT")
    private String quantificationPrejudice;

    @Column(name = "reserves", columnDefinition = "TEXT")
    private String reserves;

    @Column(name = "conclusions", columnDefinition = "TEXT")
    private String conclusions;

    /** Réserves exclues : c'est le seul champ facultatif du rapport. */
    public boolean isComplet() {
        return isPresent(titre)
                && isPresent(introduction)
                && isPresent(methodologie)
                && isPresent(informationsCollectees)
                && isPresent(exposeFactuelAnomalies)
                && isPresent(quantificationPrejudice)
                && isPresent(conclusions);
    }

    private static boolean isPresent(String value) {
        return value != null && !value.isBlank();
    }
}
