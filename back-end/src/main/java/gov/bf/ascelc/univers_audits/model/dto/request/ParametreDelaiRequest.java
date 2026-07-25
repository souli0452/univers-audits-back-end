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
public class ParametreDelaiRequest {

    @NotBlank(message = "Le libellé est obligatoire")
    @Size(max = 300)
    private String libelle;

    private Integer valeurJours;

    @NotNull(message = "L'indicateur jours ouvrables est obligatoire")
    private Boolean joursOuvrables;

    @NotNull(message = "L'indicateur actif est obligatoire")
    private Boolean actif;
}
