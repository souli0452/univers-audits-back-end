package gov.bf.ascelc.univers_audits.model.dto.response;

import gov.bf.ascelc.univers_audits.enums.AuditionStatus;
import gov.bf.ascelc.univers_audits.enums.IntervieweeType;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditionResponse {
    private UUID id;
    private UUID investigationId;
    private IntervieweeType intervieweeType;
    private String intervieweeDisplayName;
    private String conductedByName;
    private String location;
    private Instant scheduledAt;
    private Instant conductedAt;
    private AuditionStatus status;
    private String summary;
    private String cancellationReason;
}
