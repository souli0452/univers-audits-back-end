package gov.bf.ascelc.univers_audits.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.LocalDate;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JourFerieRequest {

    @NotNull(message = "La date est obligatoire")
    private LocalDate date;

    @NotBlank(message = "Le libellé est obligatoire")
    private String libelle;

    @NotNull(message = "L'indicateur actif est obligatoire")
    private Boolean actif;
}
