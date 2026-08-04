package gov.bf.ascelc.univers_audits.model.dto.response;

import gov.bf.ascelc.univers_audits.enums.RecommandationCtadp;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DecisionCGEResponse {

    private UUID id;
    private RecommandationCtadp decision;
    private String motif;
    private Instant dateDecision;
    private UUID agentCGEId;
    private String agentCGENom;
}
