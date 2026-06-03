package gov.bf.ascelc.univers_audits.model.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "permission")
@Getter @Setter @Builder
@NoArgsConstructor @AllArgsConstructor
public class Permission {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "permission_key", nullable = false, unique = true, length = 80)
    private String permissionKey;

    @Column(nullable = false, length = 200)
    private String label;

    @Column(length = 500)
    private String description;

    @Column(length = 60)
    private String category;

    @Column(nullable = false)
    @Builder.Default
    private Boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();
}