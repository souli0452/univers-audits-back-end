package gov.bf.ascelc.univers_audits.model.dto.response;

import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlanInvestigationResponse {

    private UUID id;
    private UUID investigationId;
    private Integer planVersion;
    private String objectifs;
    private String methodologie;
    private String moyensMobilises;
    private String planningProcedures;
    private Instant submittedAt;
    private UUID submittedById;
    private String submittedByNom;
    private Instant validatedAt;
    private UUID validatedById;
    private String validatedByNom;
    private Instant validationDeadline;
    private boolean overdue;
}
