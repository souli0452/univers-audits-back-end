package gov.bf.ascelc.univers_audits.model.entity;

import gov.bf.ascelc.univers_audits.abstracts.AuditEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.util.ArrayList;
import java.util.List;
@Getter
@Setter
@Entity
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "role_fonctionnel", indexes = {
        @Index(name = "idx_role_code",
                columnList = "code", unique = true)
})
public class RoleFonctionnel extends AuditEntity {
    @Column(name = "code", nullable = false,
            unique = true, length = 50)
    private String code;

    @Column(name = "libelle", nullable = false, length = 150)
    private String libelle;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "keycloak_role", length = 50)
    private String keycloakRole;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "departement_id")
    private Departement departement;

    @Column(name = "actif", nullable = false)
    @Builder.Default
    private Boolean actif = true;

    // Liste des agents ayant ce rôle.
    @OneToMany(mappedBy = "roleFonctionnel",
            fetch = FetchType.LAZY)
    @Builder.Default
    private List<AgentRole> agentRoles = new ArrayList<>();
}