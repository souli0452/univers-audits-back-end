package gov.bf.ascelc.univers_audits.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.Instant;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SuiviProcedurePenaleRequest {
    @NotNull(message = "La date de la phase est obligatoire")
    private Instant phaseAt;

    @NotBlank(message = "La phase est obligatoire")
    private String phase;

    private String commentaire;
}
