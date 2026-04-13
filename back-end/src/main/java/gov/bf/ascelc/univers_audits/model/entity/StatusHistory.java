package gov.bf.ascelc.univers_audits.model.entity;

import gov.bf.ascelc.univers_audits.enums.DossierStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;


@Getter
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "status_history", indexes = {
        // Récupération rapide de tout l'historique d'un dossier
        @Index(name = "idx_status_history_case",
                columnList = "case_id"),
        // Tri chronologique optimisé
        @Index(name = "idx_status_history_changed_at",
                columnList = "changed_at"),
        // Recherche de toutes les actions d'un agent donné
        @Index(name = "idx_status_history_agent",
                columnList = "agent_id")
})
public class StatusHistory {

    @Id
    @Column(name = "id", updatable = false,
            nullable = false, columnDefinition = "uuid")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "case_id", nullable = false,
            updatable = false)
    private Dossier dossier;

    @Enumerated(EnumType.STRING)
    @Column(name = "previous_status", length = 35,
            updatable = false)
    private DossierStatus previousStatus;


    @Enumerated(EnumType.STRING)
    @Column(name = "new_status", nullable = false,
            length = 35, updatable = false)
    private DossierStatus newStatus;

    @Column(name = "reason", columnDefinition = "TEXT",
            updatable = false)
    private String reason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agent_id", updatable = false)
    private Agent agent;

    @Column(name = "agent_full_name", length = 200,
            updatable = false)
    private String agentFullName;

    @Column(name = "changed_at", nullable = false,
            updatable = false)
    private Instant changedAt;

    @Column(name = "ip_address", length = 45, updatable = false)
    private String ipAddress;

    @PrePersist
    protected void onCreate() {

        if (id == null) {
            id = UUID.randomUUID();
        }

        if (changedAt == null) {
            changedAt = Instant.now();
        }
    }
}