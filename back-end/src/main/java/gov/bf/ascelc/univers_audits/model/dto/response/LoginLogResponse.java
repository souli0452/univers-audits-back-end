package gov.bf.ascelc.univers_audits.model.dto.response;

import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginLogResponse {

    private UUID id;
    private String agentId;
    private String agentName;
    private Boolean success;
    private String ipAddress;
    private String userAgent;
    private String failureReason;
    private Instant createdAt;
}
