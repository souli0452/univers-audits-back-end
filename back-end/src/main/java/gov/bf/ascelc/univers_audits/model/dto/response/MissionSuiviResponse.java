package gov.bf.ascelc.univers_audits.model.dto.response;

import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MissionSuiviResponse {
    private UUID id;
    private Instant missionDate;
    private String conductedByNom;
    private String objectifs;
    private String syntheseRecommandations;
    private String nouvellesRecommandations;
    private Instant submittedAt;
}
