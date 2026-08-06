package gov.bf.ascelc.univers_audits.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VisiteTerrainConductRequest {

    @NotBlank(message = "Le compte-rendu est obligatoire")
    private String summary;
}
