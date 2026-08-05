package gov.bf.ascelc.univers_audits.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IncidentObjectiviteRequest {

    @NotBlank(message = "La description de l'incident est obligatoire")
    private String description;
}
