package gov.bf.ascelc.univers_audits.model.dto.response;

import lombok.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PvAuditionResponse {
    private UUID id;
    private UUID auditionId;
    private String content;
    private String draftedByName;
    private Boolean intervieweeSigned;
    private Boolean intervieweeSignatureRefused;
    private Instant finalizedAt;
    private Integer pvVersion;
    private Instant readBackAt;
    private List<CorrectionPvAuditionResponse> corrections;
}
