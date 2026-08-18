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
public class MissionSuiviRequest {
    @NotNull(message = "La date de la mission est obligatoire")
    private Instant missionDate;

    @NotBlank(message = "Les objectifs de la mission sont obligatoires")
    private String objectifs;

    @NotBlank(message = "La synthèse des recommandations est obligatoire")
    private String syntheseRecommandations;

    private String nouvellesRecommandations;
}
