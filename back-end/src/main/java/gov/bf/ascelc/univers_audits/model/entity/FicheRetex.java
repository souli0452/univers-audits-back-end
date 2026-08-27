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

import java.math.BigDecimal;

@Getter
@Setter
@Entity
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "fiche_retex", indexes = {
        @Index(name = "idx_fiche_retex_investigation",
                columnList = "investigation_id", unique = true)
})
public class FicheRetex extends AuditEntity {

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "investigation_id", nullable = false, unique = true)
    private Investigation investigation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "type_infraction_id")
    private TypeInfraction typeInfraction;

    @Column(name = "lieu", length = 300)
    private String lieu;

    @Column(name = "difficultes_rencontrees", columnDefinition = "TEXT")
    private String difficultesRencontrees;

    @Column(name = "origine_soupcons", columnDefinition = "TEXT")
    private String origineSoupcons;

    @Column(name = "impact_financier", precision = 15, scale = 2)
    private BigDecimal impactFinancier;

    @Column(name = "originalite_schemas", columnDefinition = "TEXT")
    private String originaliteSchemas;

    @Column(name = "collaborateurs_planifies", columnDefinition = "TEXT")
    private String collaborateursPlanifies;

    @Column(name = "jours_charges")
    private Integer joursCharges;

    @Column(name = "contexte", columnDefinition = "TEXT")
    private String contexte;

    @Column(name = "strategie_methodes", columnDefinition = "TEXT")
    private String strategieMethodes;

    @Column(name = "synthese_resultats", nullable = false, columnDefinition = "TEXT")
    private String syntheseResultats;

    @Column(name = "enseignements_axes_amelioration", nullable = false, columnDefinition = "TEXT")
    private String enseignementsAxesAmelioration;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "redige_par_id", nullable = false)
    private Agent redigePar;
}
