package gov.bf.ascelc.univers_audits.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MesureConservatoireRequest {

    @NotBlank(message = "La description de la mesure conservatoire est obligatoire")
    private String description;
}
