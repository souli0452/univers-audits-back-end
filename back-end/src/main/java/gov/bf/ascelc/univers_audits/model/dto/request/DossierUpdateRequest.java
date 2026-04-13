package gov.bf.ascelc.univers_audits.model.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DossierUpdateRequest {

    @NotNull(message = "La version est obligatoire pour la mise à jour")
    private Long version;

    @Size(max = 500)
    private String object;

    @Size(max = 10000)
    private String description;

    @Size(max = 5000)
    private String motifs;

    @Size(max = 300)
    private String incidentLocation;

    @Size(max = 200)
    private String incidentPeriod;

    private BigDecimal estimatedLoss;

    private Boolean isConfidential;

    private String transferInstitution;
}