package gov.bf.ascelc.univers_audits.model.dto.request;

import gov.bf.ascelc.univers_audits.enums.TypeDesignation;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FicheAffectationAffectationRequest {

    @NotNull
    private TypeDesignation typeDesignation;

    /** Requis si typeDesignation = DEPARTEMENT (code DEI ou DAC uniquement). */
    private UUID departementDesigneId;

    /** Requis si typeDesignation = AGENT_CJ (agent actif, rôle CONSEILLER_JURIDIQUE). */
    private UUID agentDesigneId;

    private String observationsCgea;
}
