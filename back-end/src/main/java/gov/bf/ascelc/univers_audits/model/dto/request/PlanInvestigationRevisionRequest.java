package gov.bf.ascelc.univers_audits.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlanInvestigationRevisionRequest {

    @NotBlank(message = "Les objectifs de l'enquête sont obligatoires")
    private String objectifs;

    @NotBlank(message = "La méthodologie est obligatoire")
    private String methodologie;

    private String moyensMobilises;

    private String planningProcedures;

    @NotBlank(message = "Le motif de la révision est obligatoire")
    private String motifRevision;
}
