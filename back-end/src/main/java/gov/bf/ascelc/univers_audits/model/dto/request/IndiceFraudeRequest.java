package gov.bf.ascelc.univers_audits.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IndiceFraudeRequest {

    @NotBlank(message = "Le code est obligatoire")
    @Size(max = 50)
    private String code;

    @NotBlank(message = "Le libellé est obligatoire")
    @Size(max = 300)
    private String libelle;

    @Size(max = 100)
    private String categorie;

    private String description;

    @NotNull(message = "L'indicateur actif est obligatoire")
    private Boolean actif;

    private Integer ordre;
}
