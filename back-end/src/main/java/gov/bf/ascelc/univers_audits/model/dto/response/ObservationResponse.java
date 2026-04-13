package gov.bf.ascelc.univers_audits.model.dto.response;

import gov.bf.ascelc.univers_audits.enums.DossierStatus;
import gov.bf.ascelc.univers_audits.enums.ObservationType;
import lombok.*;
import java.time.Instant;
import java.util.UUID;


@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ObservationResponse {
    private UUID id;
    private ObservationType type;
    private String content;
    private Boolean confidential;
    private String authorFullName;
    private DossierStatus statusSnapshot;
    private Instant createdAt;
}