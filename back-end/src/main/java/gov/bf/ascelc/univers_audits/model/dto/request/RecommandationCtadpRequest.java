package gov.bf.ascelc.univers_audits.model.dto.request;

import gov.bf.ascelc.univers_audits.enums.RecommandationCtadp;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecommandationCtadpRequest {

    @NotNull(message = "La recommandation est obligatoire")
    private RecommandationCtadp recommandation;

    @Size(max = 2000)
    private String commentaire;
}
