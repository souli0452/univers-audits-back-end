package gov.bf.ascelc.univers_audits.model.entity;

import jakarta.persistence.*;
import lombok.*;
import gov.bf.ascelc.univers_audits.model.entity.Permission;

import java.time.Instant;
import java.util.*;

@Entity
@Table(name = "role_definition")
@Getter @Setter @Builder
@NoArgsConstructor @AllArgsConstructor
public class RoleDefinition {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "role_key", nullable = false, unique = true, length = 60)
    private String roleKey;

    @Column(nullable = false, length = 150)
    private String label;

    @Column(length = 500)
    private String description;

    @Column(nullable = false, length = 60)
    @Builder.Default
    private String icon = "pi pi-user";

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String severity = "info";

    @Column(name = "display_order", nullable = false)
    @Builder.Default
    private Integer displayOrder = 99;

    @Column(nullable = false)
    @Builder.Default
    private Boolean visible = true;

    @Column(nullable = false)
    @Builder.Default
    private Boolean active = true;

    @Column(name = "is_protected", nullable = false)
    @Builder.Default
    private Boolean isProtected = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private Instant updatedAt = Instant.now();

    @ManyToMany(fetch = FetchType.LAZY, targetEntity = Permission.class)
    @JoinTable(
            name = "role_permission",
            joinColumns        = @JoinColumn(name = "role_id"),
            inverseJoinColumns = @JoinColumn(name = "permission_id")
    )
    @Builder.Default
    private Set<Permission> permissions = new HashSet<>();

    @PreUpdate
    void onUpdate() { this.updatedAt = Instant.now(); }
}