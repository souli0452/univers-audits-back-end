package gov.bf.ascelc.univers_audits.model.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "audit_log")
@Getter @Setter @Builder
@NoArgsConstructor @AllArgsConstructor
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "agent_id",   nullable = false, length = 100)
    private String agentId;

    @Column(name = "agent_name", length = 200)
    private String agentName;

    @Column(name = "agent_role", length = 100)
    private String agentRole;

    @Column(nullable = false, length = 80)
    private String action;

    @Column(name = "entity_type", length = 60)
    private String entityType;

    @Column(name = "entity_id",   length = 100)
    private String entityId;

    @Column(length = 500)
    private String description;

    @Column(name = "ip_address",  length = 45)
    private String ipAddress;

    @Column(name = "user_agent",  length = 300)
    private String userAgent;

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String status = "SUCCESS";

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();
}