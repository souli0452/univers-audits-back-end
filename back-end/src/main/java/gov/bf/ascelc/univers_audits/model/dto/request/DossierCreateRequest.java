package gov.bf.ascelc.univers_audits.model.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import gov.bf.ascelc.univers_audits.enums.AutoReferralSource;
import gov.bf.ascelc.univers_audits.enums.QualiteDeclarant;
import gov.bf.ascelc.univers_audits.enums.SocialPlatform;
import gov.bf.ascelc.univers_audits.enums.SubmissionMode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DossierCreateRequest {

    private QualiteDeclarant quality;

    @NotNull(message = "Le mode de soumission est obligatoire")
    private SubmissionMode submissionMode;

    private SocialPlatform socialPlatform;

    private AutoReferralSource autoReferralSource;

    @Size(max = 500, message = "La référence source ne doit pas dépasser 500 caractères")
    private String sourceReference;

    @NotBlank(message = "L'objet du dossier est obligatoire")
    @Size(max = 500, message = "L'objet ne doit pas dépasser 500 caractères")
    private String object;

    @Size(max = 10000, message = "La description ne doit pas dépasser 10000 caractères")
    private String description;

    private String descriptionSource;

    @Size(max = 5000)
    private String motifs;

    @Size(max = 300, message = "La localisation ne doit pas dépasser 300 caractères")
    private String incidentLocation;

    @Size(max = 200, message = "La période ne doit pas dépasser 200 caractères")
    private String incidentPeriod;

    @Size(max = 300, message = "Le lieu de dépôt ne doit pas dépasser 300 caractères")
    private String lieuDepot;

    @Size(max = 200, message = "La dénomination de l'organisme ne doit pas dépasser 200 caractères")
    private String organismeFaitsDenomination;

    @Size(max = 300, message = "L'adresse de l'organisme ne doit pas dépasser 300 caractères")
    private String organismeFaitsAdresse;

    @Size(max = 5000, message = "Les attentes ne doivent pas dépasser 5000 caractères")
    private String attentes;

    private Boolean decisionJusticeExistante;

    @Size(max = 2000, message = "La précision ne doit pas dépasser 2000 caractères")
    private String decisionJusticePrecision;

    private Boolean autreInstitutionSaisie;

    @Size(max = 200, message = "Le nom de l'institution ne doit pas dépasser 200 caractères")
    private String autreInstitutionNom;

    @Size(max = 300, message = "L'adresse de l'institution ne doit pas dépasser 300 caractères")
    private String autreInstitutionAdresse;

    private BigDecimal estimatedLoss;

    @JsonProperty("isConfidential")
    private Boolean isConfidential;

    private UUID declarantId;


    @Valid
    private DeclarantCreateRequest declarantData;

    public Boolean getIsConfidential() {
        return isConfidential != null ? isConfidential : false;
    }

    public void setIsConfidential(Boolean isConfidential) {
        this.isConfidential = isConfidential;
    }
}