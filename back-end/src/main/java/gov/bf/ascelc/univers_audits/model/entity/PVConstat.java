package gov.bf.ascelc.univers_audits.model.entity;

import gov.bf.ascelc.univers_audits.abstracts.AuditEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

@Getter
@Setter
@Entity
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "pv_constat", indexes = {
        @Index(name = "idx_pv_constat_visite",
                columnList = "visite_terrain_id", unique = true)
})
public class PVConstat extends AuditEntity {

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "visite_terrain_id", nullable = false, unique = true)
    private VisiteTerrain visiteTerrain;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "drafted_by_id", nullable = false)
    private Agent draftedBy;
}
