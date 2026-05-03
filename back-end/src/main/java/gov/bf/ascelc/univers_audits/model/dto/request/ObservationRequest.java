package gov.bf.ascelc.univers_audits.model.dto.request;

import gov.bf.ascelc.univers_audits.enums.ObservationType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ObservationRequest {

    @NotNull(message = "Le type d'observation est obligatoire")
    private ObservationType type;

    @NotBlank(message = "Le contenu est obligatoire")
    @Size(max = 10000)
    private String content;

    /**
     * true = visible uniquement par CGE, CGEA, conseiller juridique.
     * false = visible par tous les agents ayant accès au dossier.
     */
    private Boolean confidential;
}
