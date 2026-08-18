package gov.bf.ascelc.univers_audits.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlanActionsRequest {
    @NotBlank(message = "L'entité contrôlée est obligatoire")
    private String entiteControlee;

    @NotBlank(message = "Le contenu du plan d'actions est obligatoire")
    private String contenu;
}
