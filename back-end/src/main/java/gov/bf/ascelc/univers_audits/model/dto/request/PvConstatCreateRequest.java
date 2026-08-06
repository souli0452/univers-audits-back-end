package gov.bf.ascelc.univers_audits.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PvConstatCreateRequest {

    @NotBlank(message = "Le contenu du procès-verbal de constat est obligatoire")
    private String content;
}
