package gov.bf.ascelc.univers_audits.model.entity;

import gov.bf.ascelc.univers_audits.abstracts.AuditEntity;
import gov.bf.ascelc.univers_audits.enums.TeamRole;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

@Getter
@Setter
@Entity
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "investigation_member", indexes = {
        @Index(name = "idx_inv_member_investigation",
                columnList = "investigation_id"),
        @Index(name = "idx_inv_member_agent",
                columnList = "agent_id")
})
public class InvestigationMember extends AuditEntity {


    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "investigation_id", nullable = false)
    private Investigation investigation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agent_id", nullable = false)
    private Agent agent;

    @Enumerated(EnumType.STRING)
    @Column(name = "team_role", nullable = false, length = 15)
    private TeamRole teamRole;


    @Column(name = "assigned_by", nullable = false, length = 100)
    private String assignedBy;

    @Column(name = "active", nullable = false)
    @Builder.Default
    private Boolean active = true;
}