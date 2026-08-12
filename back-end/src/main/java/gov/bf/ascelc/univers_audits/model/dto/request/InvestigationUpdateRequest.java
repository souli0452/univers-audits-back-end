package gov.bf.ascelc.univers_audits.model.dto.request;

import gov.bf.ascelc.univers_audits.enums.InvestigationOutcome;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InvestigationUpdateRequest {
    @NotNull(message = "Le résultat de l'investigation est obligatoire")
    private InvestigationOutcome outcome;
}
