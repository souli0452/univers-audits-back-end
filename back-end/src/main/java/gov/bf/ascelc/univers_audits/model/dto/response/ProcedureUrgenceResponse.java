package gov.bf.ascelc.univers_audits.model.dto.response;

import gov.bf.ascelc.univers_audits.enums.StatutProcedureUrgence;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProcedureUrgenceResponse {

    private UUID id;
    private UUID investigationId;
    private String justification;
    private UUID requestedById;
    private String requestedByNom;
    private Instant requestedAt;
    private StatutProcedureUrgence status;
    private UUID decidedById;
    private String decidedByNom;
    private Instant decidedAt;
    private String motifDecision;
}
