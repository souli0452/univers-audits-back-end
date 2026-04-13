package gov.bf.ascelc.univers_audits.model.dto.response;

import gov.bf.ascelc.univers_audits.enums.DossierStatus;
import lombok.*;
import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StatusHistoryResponse {
    private UUID id;
    private DossierStatus previousStatus;
    private DossierStatus newStatus;
    private String reason;
    private String agentFullName;
    private Instant changedAt;
}