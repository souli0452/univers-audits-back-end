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
@Table(name = "correction_pv_audition", indexes = {
        @Index(name = "idx_correction_pv_audition_pv",
                columnList = "pv_audition_id")
})
public class CorrectionPvAudition extends AuditEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pv_audition_id", nullable = false)
    private PVAudition pvAudition;

    @Column(name = "version_number", nullable = false)
    private Integer versionNumber;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "corrected_at", nullable = false)
    private Instant correctedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "corrected_by_id", nullable = false)
    private Agent correctedBy;

    @Column(name = "motif_correction", nullable = false, columnDefinition = "TEXT")
    private String motifCorrection;
}
