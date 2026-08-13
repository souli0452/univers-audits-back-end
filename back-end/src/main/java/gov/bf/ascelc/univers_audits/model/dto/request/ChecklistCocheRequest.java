package gov.bf.ascelc.univers_audits.model.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChecklistCocheRequest {
    @NotNull(message = "L'état coché/non coché est obligatoire")
    private Boolean coche;
    private String commentaire;
}
