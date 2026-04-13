package gov.bf.ascelc.univers_audits.model.dto.request;

import gov.bf.ascelc.univers_audits.enums.InvestigationOutcome;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InvestigationUpdateRequest {

    @NotBlank(message = "Le rapport final est obligatoire")
    private String finalReport;

    @NotBlank(message = "Les conclusions sont obligatoires")
    private String conclusions;

    private String recommendations;

    private InvestigationOutcome outcome;
}