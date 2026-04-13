package gov.bf.ascelc.univers_audits.model.dto.response;

import gov.bf.ascelc.univers_audits.enums.InvestigationStatus;
import lombok.*;
import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InvestigationSummaryResponse {
    private UUID id;
    private InvestigationStatus status;
    private Instant startDate;
    private Instant plannedEndDate;
    private Instant extendedDeadline;
    private Integer plannedDurationDays;
    private Long remainingDays;
    private Boolean overdue;
    private Integer memberCount;
}