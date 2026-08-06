package gov.bf.ascelc.univers_audits.model.dto.response;

import gov.bf.ascelc.univers_audits.enums.AuditionStatus;
import gov.bf.ascelc.univers_audits.enums.IntervieweeType;
import lombok.*;

import java.time.Instant;
import java.util.List;
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
    private List<String> investigatorNames;
    private String location;
    private Instant scheduledAt;
    private Instant conductedAt;
    private AuditionStatus status;
    private String summary;
    private String cancellationReason;
    private String noShowNote;
    private String orderWarning;
    private String secondAuditionWarning;
}
