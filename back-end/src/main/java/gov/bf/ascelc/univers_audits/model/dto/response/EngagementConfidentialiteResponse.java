package gov.bf.ascelc.univers_audits.model.dto.response;

import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EngagementConfidentialiteResponse {

    private UUID id;
    private UUID investigationId;
    private UUID agentId;
    private String agentNom;
    private Boolean hasConflictOfInterest;
    private String conflictDetails;
    private Instant signedAt;
}
