package gov.bf.ascelc.univers_audits.model.entity;

import gov.bf.ascelc.univers_audits.abstracts.AuditEntity;
import gov.bf.ascelc.univers_audits.enums.*;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "dossier")
public class Dossier extends AuditEntity {

    private String number;
    private String object;
    private String description;
    private DossierStatus statut;
    private SubmissionMode submissionMode;
    private String codeAcces;
    private TypeSaisine type;
    private SocialPlatform socialPlatform;
    private AutoReferralSource autoReferralSource;
    private String sourceReference;
    private String incidentLocation;
    private String descriptionSource;
    private Double estimatedLoss;
    private LocalDateTime receptionDate;
    private LocalDateTime acknowledgmentDeadline;
    private LocalDateTime additionalInfoDeadline;
    private LocalDateTime eligibilityDecisionDate;
    private LocalDateTime transferDate;
    private String transferInstitution;
    private Boolean isConfidential;

    private Integer version;
}
