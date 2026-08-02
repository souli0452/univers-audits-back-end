package gov.bf.ascelc.univers_audits.model.dto.request;

import gov.bf.ascelc.univers_audits.enums.NatureQualification;
import gov.bf.ascelc.univers_audits.enums.QualificationNonPenale;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EtudeOpportuniteRequest {

    private Boolean preoccupationReelle;

    @Size(max = 2000)
    private String preoccupationReelleCommentaire;

    private Boolean competenceAsceLc;

    @Size(max = 2000)
    private String competenceAsceLcCommentaire;

    private NatureQualification natureQualification;

    private UUID typeInfractionId;

    private QualificationNonPenale qualificationNonPenale;

    private Boolean preuvesSuffisantes;

    @Size(max = 2000)
    private String preuvesSuffisantesCommentaire;

    private Boolean enqueteComplementaireNecessaire;

    @Size(max = 2000)
    private String enqueteComplementaireNecessaireCommentaire;

    private Boolean urgenceSecurisationPreuves;

    @Size(max = 2000)
    private String urgenceSecurisationPreuvesCommentaire;

    private Boolean opportuniteSaisirProcureur;

    @Size(max = 2000)
    private String opportuniteSaisirProcureurCommentaire;

    private Boolean secteurSensible;

    @Size(max = 300)
    private String secteurPrecision;

    private Boolean soliditeAllegation;

    @Size(max = 2000)
    private String soliditeAllegationCommentaire;

    @Size(max = 5000)
    private String avisGeneral;
}
