package gov.bf.ascelc.univers_audits.model.entity;

import gov.bf.ascelc.univers_audits.abstracts.AuditEntity;
import gov.bf.ascelc.univers_audits.enums.DecisionCgeAffectation;
import gov.bf.ascelc.univers_audits.enums.EtatAvancementAffectation;
import gov.bf.ascelc.univers_audits.enums.TypeDesignation;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.time.Instant;

@Getter
@Setter
@Entity
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "fiche_affectation", indexes = {
        @Index(name = "idx_fiche_affectation_dossier",
                columnList = "dossier_id", unique = true)
})
public class FicheAffectation extends AuditEntity {

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dossier_id", nullable = false, unique = true)
    private Dossier dossier;

    // ── Section 2 : Transmission par le Cabinet du CGE ──────────────
    @Enumerated(EnumType.STRING)
    @Column(name = "decision_cge", length = 30)
    private DecisionCgeAffectation decisionCge;

    @Column(name = "observations_cge", columnDefinition = "TEXT")
    private String observationsCge;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agent_cge_id")
    private Agent agentCge;

    @Column(name = "date_decision_cge")
    private Instant dateDecisionCge;

    // ── Section 3 : Affectation par le CGEA ─────────────────────────
    @Enumerated(EnumType.STRING)
    @Column(name = "type_designation", length = 20)
    private TypeDesignation typeDesignation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "departement_designe_id")
    private Departement departementDesigne;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agent_designe_id")
    private Agent agentDesigne;

    @Column(name = "observations_cgea", columnDefinition = "TEXT")
    private String observationsCgea;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agent_cgea_id")
    private Agent agentCgea;

    @Column(name = "date_imputation")
    private Instant dateImputation;

    // ── Section 4 : Suivi et traçabilité ────────────────────────────
    @Column(name = "date_retour")
    private Instant dateRetour;

    @Enumerated(EnumType.STRING)
    @Column(name = "etat_avancement", length = 20)
    private EtatAvancementAffectation etatAvancement;

    @Column(name = "etat_avancement_precision", length = 300)
    private String etatAvancementPrecision;

    @Column(name = "commentaires_suivi", columnDefinition = "TEXT")
    private String commentairesSuivi;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agent_suivi_id")
    private Agent agentSuivi;
}
