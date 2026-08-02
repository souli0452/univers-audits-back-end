package gov.bf.ascelc.univers_audits.model.entity;

import gov.bf.ascelc.univers_audits.abstracts.AuditEntity;
import gov.bf.ascelc.univers_audits.enums.NatureQualification;
import gov.bf.ascelc.univers_audits.enums.QualificationNonPenale;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

@Getter
@Setter
@Entity
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "etude_opportunite", indexes = {
        @Index(name = "idx_etude_opportunite_dossier",
                columnList = "dossier_id", unique = true)
})
public class EtudeOpportunite extends AuditEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dossier_id", nullable = false, unique = true)
    private Dossier dossier;

    @Column(name = "preoccupation_reelle")
    private Boolean preoccupationReelle;

    @Column(name = "preoccupation_reelle_commentaire", length = 2000)
    private String preoccupationReelleCommentaire;

    @Column(name = "competence_asce_lc")
    private Boolean competenceAsceLc;

    @Column(name = "competence_asce_lc_commentaire", length = 2000)
    private String competenceAsceLcCommentaire;

    @Enumerated(EnumType.STRING)
    @Column(name = "nature_qualification", length = 20)
    private NatureQualification natureQualification;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "type_infraction_id")
    private TypeInfraction typeInfraction;

    @Enumerated(EnumType.STRING)
    @Column(name = "qualification_non_penale", length = 30)
    private QualificationNonPenale qualificationNonPenale;

    @Column(name = "preuves_suffisantes")
    private Boolean preuvesSuffisantes;

    @Column(name = "preuves_suffisantes_commentaire", length = 2000)
    private String preuvesSuffisantesCommentaire;

    @Column(name = "enquete_complementaire_necessaire")
    private Boolean enqueteComplementaireNecessaire;

    @Column(name = "enquete_complementaire_necessaire_commentaire", length = 2000)
    private String enqueteComplementaireNecessaireCommentaire;

    @Column(name = "urgence_securisation_preuves")
    private Boolean urgenceSecurisationPreuves;

    @Column(name = "urgence_securisation_preuves_commentaire", length = 2000)
    private String urgenceSecurisationPreuvesCommentaire;

    @Column(name = "opportunite_saisir_procureur")
    private Boolean opportuniteSaisirProcureur;

    @Column(name = "opportunite_saisir_procureur_commentaire", length = 2000)
    private String opportuniteSaisirProcureurCommentaire;

    @Column(name = "secteur_sensible")
    private Boolean secteurSensible;

    @Column(name = "secteur_precision", length = 300)
    private String secteurPrecision;

    @Column(name = "solidite_allegation")
    private Boolean soliditeAllegation;

    @Column(name = "solidite_allegation_commentaire", length = 2000)
    private String soliditeAllegationCommentaire;

    @Column(name = "avis_general", length = 5000)
    private String avisGeneral;
}
