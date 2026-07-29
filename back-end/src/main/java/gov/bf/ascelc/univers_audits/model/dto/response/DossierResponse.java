package gov.bf.ascelc.univers_audits.model.dto.response;

import gov.bf.ascelc.univers_audits.enums.AutoReferralSource;
import gov.bf.ascelc.univers_audits.enums.DossierStatus;
import gov.bf.ascelc.univers_audits.enums.QualiteDeclarant;
import gov.bf.ascelc.univers_audits.enums.SocialPlatform;
import gov.bf.ascelc.univers_audits.enums.SubmissionMode;
import gov.bf.ascelc.univers_audits.enums.TypeSaisine;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DossierResponse {

    private UUID                       id;
    private String                     number;
    private String                     accessCode;
    private Long                       version;
    private DossierStatus              status;
    private TypeSaisine                type;
    private QualiteDeclarant           quality;
    private SubmissionMode             submissionMode;
    private SocialPlatform             socialPlatform;
    private AutoReferralSource         autoReferralSource;
    private String                     sourceReference;
    private String                     object;
    private String                     description;
    private String                     descriptionSource;
    private String                     motifs;
    private String                     incidentLocation;
    private String                     incidentPeriod;
    private BigDecimal                 estimatedLoss;
    private Boolean                    isConfidential;
    private Instant                    receptionDate;
    private Instant                    acknowledgmentDeadline;
    private Instant                    additionalInfoDeadline;
    private Instant                    eligibilityDecisionDate;
    private Instant                    transferDate;
    private String                     transferInstitution;
    private Instant                    closingDate;

    /**
     * Motif de la demande de complément.
     * Rempli uniquement quand status = EN_ATTENTE_COMPLEMENT.
     * Contenu de la dernière observation de type COMPLEMENT_REQUEST.
     * Affiché sur le portail citoyen pour indiquer ce qui est attendu.
     */
    private String                     complementMotif;

    private Boolean                    acknowledgmentOverdue;
    private Long                       daysSinceReception;
    private DeclarantResponse          declarant;
    private AgentSummaryResponse       agentInCharge;
    private List<TargetedPartyResponse>  targetedParties;
    private List<WitnessResponse>        witnesses;
    private List<ObservationResponse>    observations;
    private List<AttachmentResponse>     attachments;
    private List<NotificationResponse>   notifications;
    private InvestigationSummaryResponse investigation;
    private Instant                    createdAt;
    private Instant                    updatedAt;
    private String                     createdById;
}