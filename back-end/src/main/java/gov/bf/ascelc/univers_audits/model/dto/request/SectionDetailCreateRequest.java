package gov.bf.ascelc.univers_audits.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SectionDetailCreateRequest {

    @NotBlank(message = "Le libelle de la section est obligatoire")
    private String libelle;
}
