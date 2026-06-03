package gov.bf.ascelc.univers_audits.model.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "portal_config")
@Getter @Setter @Builder
@NoArgsConstructor @AllArgsConstructor
public class PortalConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "config_key", nullable = false, unique = true, length = 80)
    private String configKey;

    @Column(name = "config_value", columnDefinition = "TEXT")
    private String configValue;

    @Column(nullable = false, length = 200)
    private String label;

    @Column(length = 500)
    private String description;

    @Column(name = "value_type", nullable = false, length = 20)
    @Builder.Default
    private String valueType = "TEXT";

    @Column(name = "group_name", nullable = false, length = 60)
    @Builder.Default
    private String groupName = "GENERAL";

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private Instant updatedAt = Instant.now();

    @Column(name = "updated_by", length = 200)
    private String updatedBy;

    @PreUpdate
    void onUpdate() { this.updatedAt = Instant.now(); }
}