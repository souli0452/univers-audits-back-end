package gov.bf.ascelc.univers_audits.model.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "login_log")
@Getter @Setter @Builder
@NoArgsConstructor @AllArgsConstructor
public class LoginLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "agent_id",   nullable = false, length = 100)
    private String agentId;

    @Column(name = "agent_name", length = 200)
    private String agentName;

    @Column(nullable = false)
    @Builder.Default
    private Boolean success = true;

    @Column(name = "ip_address",     length = 45)
    private String ipAddress;

    @Column(name = "user_agent",     length = 300)
    private String userAgent;

    @Column(name = "failure_reason", length = 200)
    private String failureReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();
}