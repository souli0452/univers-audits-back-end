package gov.bf.ascelc.univers_audits.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProcedureUrgenceRequest {

    @NotBlank(message = "La justification de la procédure d'urgence est obligatoire")
    private String justification;
}
