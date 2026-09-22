package gov.bf.ascelc.univers_audits.model.dto.request;

import gov.bf.ascelc.univers_audits.enums.DecisionCgeAffectation;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FicheAffectationCreateRequest {

    @NotNull
    private DecisionCgeAffectation decisionCge;

    private String observationsCge;
}
