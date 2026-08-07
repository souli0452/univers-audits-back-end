package gov.bf.ascelc.univers_audits.model.dto.response;

import gov.bf.ascelc.univers_audits.enums.VisiteStatus;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VisiteTerrainResponse {
    private UUID id;
    private UUID investigationId;
    private String plannedByName;
    private String location;
    private Instant scheduledAt;
    private Instant conductedAt;
    private VisiteStatus status;
    private String summary;
    private String cancellationReason;
    private String carenceReason;
}
