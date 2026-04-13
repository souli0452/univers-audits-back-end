package gov.bf.ascelc.univers_audits.model.dto.response;

import lombok.*;

import java.math.BigDecimal;
import java.util.Map;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StatistiqueResponse {

    private Long totalDossiers;
    private Map<String, Long> countByStatus;
    private Map<String, Long> countBySubmissionMode;
    private Map<String, Long> countByType;
    private Long closedInsufficientEvidence;
    private Long closedFantasist;
    private Long referredToJustice;
    private Long transferred;
    private Long inInvestigation;
    private Double admissibilityRate;
    private BigDecimal totalEstimatedLoss;
    private BigDecimal averageEstimatedLoss;
    private Double avgRegistrationDelayDays;
    private Double avgOpportunityStudyDelayDays;
    private Double avgInvestigationDurationDays;
    private Long overdueAcknowledgments;
    private Long overdueInvestigations;
    private Double deadlineComplianceRate;
    private String period;
    private String generatedAt;
}