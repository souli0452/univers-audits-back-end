package gov.bf.ascelc.univers_audits.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PvAuditionCorrectionRequest {

    @NotBlank(message = "Le contenu corrigé du procès-verbal est obligatoire")
    private String content;

    @NotBlank(message = "Le motif de la correction est obligatoire")
    private String motifCorrection;
}
