package gov.bf.ascelc.univers_audits.model.entity;

import gov.bf.ascelc.univers_audits.abstracts.AuditEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.time.LocalDate;

@Getter
@Setter
@Entity
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "agent_role", indexes = {
        @Index(name = "idx_agent_role_agent",
                columnList = "agent_id"),
        @Index(name = "idx_agent_role_role",
                columnList = "role_fonctionnel_id")
})
public class AgentRole extends AuditEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agent_id", nullable = false)
    private Agent agent;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "role_fonctionnel_id", nullable = false)
    private RoleFonctionnel roleFonctionnel;

    @Column(name = "date_attribution", nullable = false)
    private LocalDate dateAttribution;

    @Column(name = "date_fin")
    private LocalDate dateFin;

    @Column(name = "attribue_par", length = 100)
    private String attribuePar;

    @Column(name = "actif", nullable = false)
    @Builder.Default
    private Boolean actif = true;

    @PrePersist
    protected void onCreate() {
        if (dateAttribution == null) {
            dateAttribution = LocalDate.now();
        }
    }
}