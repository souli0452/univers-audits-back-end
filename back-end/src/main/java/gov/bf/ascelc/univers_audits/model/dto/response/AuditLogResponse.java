package gov.bf.ascelc.univers_audits.model.dto.response;

import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLogResponse {

    private UUID id;
    private String agentId;
    private String agentName;
    private String agentRole;
    private String action;
    private String entityType;
    private String entityId;
    private String description;
    private String ipAddress;
    private String userAgent;
    private String status;
    private Instant createdAt;
}
