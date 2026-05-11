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
    private Long referredToJustice;
    private Double admissibilityRate;
    private BigDecimal totalEstimatedLoss;
    private Double avgRegistrationDelayDays;
    private Double avgInvestigationDurationDays;
    private Long overdueAcknowledgments;
    private Long overdueInvestigations;
    private String period;
    private String generatedAt;
}