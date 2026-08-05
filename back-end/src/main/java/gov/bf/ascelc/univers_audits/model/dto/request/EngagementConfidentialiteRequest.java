package gov.bf.ascelc.univers_audits.model.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EngagementConfidentialiteRequest {

    @NotNull(message = "La déclaration de conflit d'intérêts est obligatoire")
    private Boolean hasConflictOfInterest;

    private String conflictDetails;
}
