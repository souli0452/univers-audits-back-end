package gov.bf.ascelc.univers_audits.model.entity;

import gov.bf.ascelc.univers_audits.abstracts.AuditEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.time.Instant;

@Getter
@Setter
@Entity
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "mission_suivi", indexes = {
        @Index(name = "idx_mission_suivi_investigation",
                columnList = "investigation_id")
})
public class MissionSuivi extends AuditEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "investigation_id", nullable = false)
    private Investigation investigation;

    @Column(name = "mission_date", nullable = false)
    private Instant missionDate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "conducted_by_id", nullable = false)
    private Agent conductedBy;

    @Column(name = "objectifs", nullable = false, columnDefinition = "TEXT")
    private String objectifs;

    @Column(name = "synthese_recommandations", nullable = false, columnDefinition = "TEXT")
    private String syntheseRecommandations;

    @Column(name = "nouvelles_recommandations", columnDefinition = "TEXT")
    private String nouvellesRecommandations;

    @Column(name = "submitted_at", nullable = false)
    private Instant submittedAt;
}
