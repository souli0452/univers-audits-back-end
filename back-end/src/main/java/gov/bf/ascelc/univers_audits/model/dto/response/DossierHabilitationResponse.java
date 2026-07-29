package gov.bf.ascelc.univers_audits.model.dto.response;

import gov.bf.ascelc.univers_audits.enums.HabilitationSource;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DossierHabilitationResponse {

    private UUID id;
    private AgentSummaryResponse agent;
    private HabilitationSource source;
    private AgentSummaryResponse grantedBy;
    private String reason;
    private Instant grantedAt;
    private Instant revokedAt;
    private AgentSummaryResponse revokedBy;
    private String revocationReason;
    private boolean active;
}
