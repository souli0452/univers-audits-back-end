package gov.bf.ascelc.univers_audits.model.dto.response;

import gov.bf.ascelc.univers_audits.enums.DecisionCgeAffectation;
import gov.bf.ascelc.univers_audits.enums.EtatAvancementAffectation;
import gov.bf.ascelc.univers_audits.enums.TypeDesignation;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FicheAffectationResponse {

    private UUID id;
    private UUID dossierId;

    private DecisionCgeAffectation decisionCge;
    private String observationsCge;
    private String agentCgeNom;
    private Instant dateDecisionCge;

    private TypeDesignation typeDesignation;
    private UUID departementDesigneId;
    private String departementDesigneLibelle;
    private UUID agentDesigneId;
    private String agentDesigneNom;
    private String observationsCgea;
    private String agentCgeaNom;
    private Instant dateImputation;

    private Instant dateRetour;
    private EtatAvancementAffectation etatAvancement;
    private String etatAvancementPrecision;
    private String commentairesSuivi;
    private String agentSuiviNom;

    private Instant createdAt;
    private Instant updatedAt;
}
