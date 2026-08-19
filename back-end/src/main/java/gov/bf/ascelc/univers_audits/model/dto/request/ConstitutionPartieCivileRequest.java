package gov.bf.ascelc.univers_audits.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConstitutionPartieCivileRequest {
    @NotBlank(message = "La justification est obligatoire")
    private String justification;

    private BigDecimal montantReclame;
}
