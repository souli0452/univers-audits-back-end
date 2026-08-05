package gov.bf.ascelc.univers_audits.model.dto.response;

import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RevisionPlanResponse {

    private UUID id;
    private Integer versionNumber;
    private String objectifs;
    private String methodologie;
    private String moyensMobilises;
    private String planningProcedures;
    private Instant revisedAt;
    private UUID revisedById;
    private String revisedByNom;
    private String motifRevision;
}
