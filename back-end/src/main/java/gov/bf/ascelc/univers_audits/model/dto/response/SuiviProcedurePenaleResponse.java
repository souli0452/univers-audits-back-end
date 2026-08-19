package gov.bf.ascelc.univers_audits.model.dto.response;

import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SuiviProcedurePenaleResponse {
    private UUID id;
    private Instant phaseAt;
    private String phase;
    private String commentaire;
    private String agentNom;
    private Instant submittedAt;
}
