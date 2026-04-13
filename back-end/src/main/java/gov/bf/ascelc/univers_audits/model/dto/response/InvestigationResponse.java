package gov.bf.ascelc.univers_audits.model.dto.response;

import gov.bf.ascelc.univers_audits.enums.InvestigationOutcome;
import gov.bf.ascelc.univers_audits.enums.InvestigationStatus;
import gov.bf.ascelc.univers_audits.enums.TeamRole;
import lombok.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InvestigationResponse {

    private UUID id;
    private UUID dossierId;
    private String dossierNumber;
    private String dossierObject;
    private InvestigationStatus status;
    private Instant startDate;
    private Integer plannedDurationDays;
    private Instant plannedEndDate;
    private Instant actualEndDate;
    private Instant extendedDeadline;
    private String extensionReason;
    private Boolean overdue;
    private Long remainingDays;
    private String finalReport;
    private String conclusions;
    private String recommendations;
    private InvestigationOutcome outcome;
    private Instant reportSubmittedAt;
    private Instant deiApprovedAt;
    private Instant legalAdvisorApprovedAt;
    private Instant cgeApprovedAt;
    private AgentSummaryResponse cgea;
    private List<InvestigationMemberResponse> members;
    private Instant createdAt;
    private Instant updatedAt;
}