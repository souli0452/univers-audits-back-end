package gov.bf.ascelc.univers_audits.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import gov.bf.ascelc.univers_audits.abstracts.AuditEntity;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Getter
@Setter
@Entity
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "lecon_a_partager", indexes = {
        @Index(name = "idx_lecon_fiche_retex",
                columnList = "fiche_retex_id", unique = true)
})
public class LeconAPartager extends AuditEntity {

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fiche_retex_id", nullable = false, unique = true)
    private FicheRetex ficheRetex;

    @Column(name = "titre", nullable = false, length = 300)
    private String titre;

    @Column(name = "resume", nullable = false, columnDefinition = "TEXT")
    private String resume;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "publiee_par_id", nullable = false)
    private Agent publieePar;
}
