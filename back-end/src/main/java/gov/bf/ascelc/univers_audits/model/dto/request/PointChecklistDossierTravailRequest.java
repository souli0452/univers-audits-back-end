package gov.bf.ascelc.univers_audits.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PointChecklistDossierTravailRequest {
    @NotBlank(message = "Le libellé est obligatoire")
    private String libelle;
    private String categorie;
    @NotNull(message = "L'ordre est obligatoire")
    private Integer ordre;
    private Boolean actif;
}
