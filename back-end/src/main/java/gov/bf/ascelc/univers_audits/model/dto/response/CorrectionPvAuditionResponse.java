package gov.bf.ascelc.univers_audits.model.dto.response;

import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CorrectionPvAuditionResponse {
    private UUID id;
    private Integer versionNumber;
    private String content;
    private Instant correctedAt;
    private UUID correctedById;
    private String correctedByName;
    private String motifCorrection;
}
