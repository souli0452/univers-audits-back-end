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
@Table(name = "note_recommandations")
public class NoteRecommandations extends AuditEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rapport_enquete_id", nullable = false, unique = true)
    private RapportEnquete rapportEnquete;

    @Column(name = "contenu", columnDefinition = "TEXT")
    private String contenu;

    public boolean isComplet() {
        return contenu != null && !contenu.isBlank();
    }
}
