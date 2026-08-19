package gov.bf.ascelc.univers_audits.model.dto.response;

import lombok.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MissionSuiviListResponse {
    private UUID investigationId;
    private Instant missionSuiviDueAt;
    private boolean missionSuiviOverdue;
    private List<MissionSuiviResponse> missions;
}
