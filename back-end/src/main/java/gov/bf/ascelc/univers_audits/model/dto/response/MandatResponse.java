package gov.bf.ascelc.univers_audits.model.dto.response;

import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MandatResponse {

    private UUID id;
    private UUID investigationId;
    private Instant dateDelivrance;
    private UUID agentCGEId;
    private String agentCGENom;
}
