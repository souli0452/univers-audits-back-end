package gov.bf.ascelc.univers_audits.model.entity;

import gov.bf.ascelc.univers_audits.abstracts.AuditEntity;
import gov.bf.ascelc.univers_audits.enums.AutoReferralSource;
import gov.bf.ascelc.univers_audits.enums.DossierPriority;
import gov.bf.ascelc.univers_audits.enums.DossierStatus;
import gov.bf.ascelc.univers_audits.enums.OrganisationDetail;
import gov.bf.ascelc.univers_audits.enums.QualiteDeclarant;
import gov.bf.ascelc.univers_audits.enums.SocialPlatform;
import gov.bf.ascelc.univers_audits.enums.SubmissionMode;
import gov.bf.ascelc.univers_audits.enums.TypeSaisine;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Entity
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "dossier", indexes = {
        @Index(name = "idx_dossier_number",
                columnList = "number", unique = true),
        @Index(name = "idx_dossier_access_code",
                columnList = "access_code", unique = true),
        @Index(name = "idx_dossier_status",
                columnList = "status"),
        @Index(name = "idx_dossier_declarant",
                columnList = "declarant_id"),
        @Index(name = "idx_dossier_agent",
                columnList = "agent_in_charge_id"),
        @Index(name = "idx_dossier_reception",
                columnList = "reception_date")
})
public class Dossier extends AuditEntity {
    @Column(name = "number", unique = true, length = 20)
    private String number;

    @Column(name = "access_code", unique = true,
            nullable = false, length = 10)
    private String accessCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 35)
    @Builder.Default
    private DossierStatus status = DossierStatus.SOUMIS;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private TypeSaisine type;

    @Enumerated(EnumType.STRING)
    @Column(name = "quality", length = 30)
    private QualiteDeclarant quality;

    @Column(name = "anonymous", nullable = false)
    @Builder.Default
    private Boolean anonymous = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "submission_mode", length = 25)
    private SubmissionMode submissionMode;

    @Enumerated(EnumType.STRING)
    @Column(name = "social_platform", length = 20)
    private SocialPlatform socialPlatform;

    @Enumerated(EnumType.STRING)
    @Column(name = "auto_referral_source", length = 30)
    private AutoReferralSource autoReferralSource;

    @Enumerated(EnumType.STRING)
    @Column(name = "organisation_detail", length = 25)
    private OrganisationDetail organisationDetail;

    @Column(name = "source_reference", length = 500)
    private String sourceReference;

    @Column(name = "object", nullable = false, length = 500)
    private String object;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "description_source", length = 30)
    private String descriptionSource;

    @Column(name = "motifs", columnDefinition = "TEXT")
    private String motifs;

    @Column(name = "incident_location", length = 300)
    private String incidentLocation;

    @Column(name = "incident_period", length = 200)
    private String incidentPeriod;

    @Column(name = "lieu_depot", length = 300)
    private String lieuDepot;

    @Column(name = "organisme_faits_denomination", length = 200)
    private String organismeFaitsDenomination;

    @Column(name = "organisme_faits_adresse", length = 300)
    private String organismeFaitsAdresse;

    @Column(name = "attentes", columnDefinition = "TEXT")
    private String attentes;

    @Column(name = "decision_justice_existante", nullable = false)
    @Builder.Default
    private Boolean decisionJusticeExistante = false;

    @Column(name = "decision_justice_precision", columnDefinition = "TEXT")
    private String decisionJusticePrecision;

    @Column(name = "autre_institution_saisie", nullable = false)
    @Builder.Default
    private Boolean autreInstitutionSaisie = false;

    @Column(name = "autre_institution_nom", length = 200)
    private String autreInstitutionNom;

    @Column(name = "autre_institution_adresse", length = 300)
    private String autreInstitutionAdresse;

    @Column(name = "estimated_loss", precision = 15, scale = 2)
    private BigDecimal estimatedLoss;

    @Column(name = "is_confidential", nullable = false)
    @Builder.Default
    private Boolean isConfidential = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "declarant_id")
    private Declarant declarant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agent_in_charge_id")
    private Agent agentInCharge;

    @Column(name = "reception_date")
    private Instant receptionDate;

    @Column(name = "acknowledgment_deadline")
    private Instant acknowledgmentDeadline;

    @Column(name = "additional_info_deadline")
    private Instant additionalInfoDeadline;

    @Column(name = "eligibility_decision_date")
    private Instant eligibilityDecisionDate;

    @Column(name = "transfer_date")
    private Instant transferDate;

    @Column(name = "transfer_institution", length = 300)
    private String transferInstitution;

    @Column(name = "closing_date")
    private Instant closingDate;

    // ── Priorité ──────────────────────────────────────────────────
    @Enumerated(EnumType.STRING)
    @Column(name = "priority", length = 20)
    @Builder.Default
    private DossierPriority priority = DossierPriority.NORMAL;

    @Column(name = "priority_reason", length = 500)
    private String priorityReason;

    @Column(name = "priority_deadline")
    private Instant priorityDeadline;

    @Column(name = "priority_set_at")
    private Instant prioritySetAt;


    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "priority_set_by_id")
    private Agent prioritySetBy;

    // ── Relations ─────────────────────────────────────────────────

    @OneToMany(mappedBy = "dossier",
            cascade = CascadeType.ALL,
            orphanRemoval = true)
    @Builder.Default
    private List<TargetedParty> targetedParties = new ArrayList<>();

    @OneToMany(mappedBy = "dossier",
            cascade = CascadeType.ALL,
            orphanRemoval = true)
    @Builder.Default
    private List<Witness> witnesses = new ArrayList<>();

    // observations/notifications/statusHistory sont des traces d'audit
    // immuables (append-only) — cascade limité à PERSIST/MERGE et pas
    // d'orphanRemoval, pour qu'un retrait accidentel de la collection en
    // mémoire (ex: un removeIf) ne supprime jamais une ligne en base.
    @OneToMany(mappedBy = "dossier",
            cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    @OrderBy("createdAt ASC")
    @Builder.Default
    private List<Observation> observations = new ArrayList<>();

    @OneToMany(mappedBy = "dossier",
            cascade = CascadeType.ALL,
            orphanRemoval = true)
    @Builder.Default
    private List<Attachment> attachments = new ArrayList<>();

    @OneToMany(mappedBy = "dossier",
            cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    @Builder.Default
    private List<Notification> notifications = new ArrayList<>();

    @OneToMany(mappedBy = "dossier",
            cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    @OrderBy("changedAt ASC")
    @Builder.Default
    private List<StatusHistory> statusHistory = new ArrayList<>();

    @OneToOne(mappedBy = "dossier", fetch = FetchType.LAZY)
    private Investigation investigation;

    // ── Méthodes métier ───────────────────────────────────────────

    public void registerReception(Agent agent,
                                   Instant receptionDate,
                                   Instant acknowledgmentDeadline,
                                   Instant additionalInfoDeadline) {
        this.receptionDate          = receptionDate;
        this.agentInCharge          = agent;
        this.acknowledgmentDeadline = acknowledgmentDeadline;
        this.additionalInfoDeadline = additionalInfoDeadline;
        this.status = DossierStatus.RECU;
    }

    public boolean isAcknowledgmentOverdue() {
        return acknowledgmentDeadline != null
                && Instant.now().isAfter(acknowledgmentDeadline)
                && !DossierStatus.CLOS.equals(status)
                && !DossierStatus.CLASSE.equals(status);
    }

    public boolean isComplementOverdue() {
        return additionalInfoDeadline != null
                && Instant.now().isAfter(additionalInfoDeadline)
                && DossierStatus.EN_ATTENTE_COMPLEMENT.equals(status);
    }

    public boolean isClosed() {
        return DossierStatus.CLOS.equals(status)
                || DossierStatus.CLASSE.equals(status);
    }

    public boolean isAdmissible() {
        return DossierStatus.RECEVABLE.equals(status)
                || DossierStatus.EN_INVESTIGATION.equals(status)
                || DossierStatus.RAPPORT_PRODUIT.equals(status)
                || DossierStatus.DECISION_RENDUE.equals(status)
                || DossierStatus.CLOS.equals(status);
    }

    public long getDaysSinceReception() {
        if (receptionDate == null) return 0;
        long seconds = Instant.now().getEpochSecond()
                - receptionDate.getEpochSecond();
        return seconds / 86400;
    }
}