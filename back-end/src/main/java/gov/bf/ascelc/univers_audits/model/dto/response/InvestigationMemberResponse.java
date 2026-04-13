package gov.bf.ascelc.univers_audits.model.dto.response;

import gov.bf.ascelc.univers_audits.enums.TeamRole;
import lombok.*;

import java.time.LocalDate;
import java.util.UUID;


@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InvestigationMemberResponse {
    private UUID id;
    private AgentSummaryResponse agent;
    private TeamRole teamRole;
    private LocalDate dateAttribution;
    private Boolean active;
}