package gov.bf.ascelc.univers_audits.model.dto.response;

import gov.bf.ascelc.univers_audits.enums.NatureQualification;
import gov.bf.ascelc.univers_audits.enums.QualificationNonPenale;
import lombok.*;

import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EtudeOpportuniteResponse {

    private UUID id;
    private Boolean preoccupationReelle;
    private String preoccupationReelleCommentaire;
    private Boolean competenceAsceLc;
    private String competenceAsceLcCommentaire;
    private NatureQualification natureQualification;
    private UUID typeInfractionId;
    private String typeInfractionLibelle;
    private QualificationNonPenale qualificationNonPenale;
    private Boolean preuvesSuffisantes;
    private String preuvesSuffisantesCommentaire;
    private Boolean enqueteComplementaireNecessaire;
    private String enqueteComplementaireNecessaireCommentaire;
    private Boolean urgenceSecurisationPreuves;
    private String urgenceSecurisationPreuvesCommentaire;
    private Boolean opportuniteSaisirProcureur;
    private String opportuniteSaisirProcureurCommentaire;
    private Boolean secteurSensible;
    private String secteurPrecision;
    private Boolean soliditeAllegation;
    private String soliditeAllegationCommentaire;
    private String avisGeneral;
}
