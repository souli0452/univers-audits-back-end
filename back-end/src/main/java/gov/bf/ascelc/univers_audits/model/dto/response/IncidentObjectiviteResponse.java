package gov.bf.ascelc.univers_audits.model.dto.response;

import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IncidentObjectiviteResponse {

    private UUID id;
    private UUID investigationId;
    private UUID declaredById;
    private String declaredByNom;
    private String description;
    private Instant declaredAt;
}
