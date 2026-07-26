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
@Table(name = "pv_audition", indexes = {
        @Index(name = "idx_pv_audition_audition",
                columnList = "audition_id", unique = true)
})
public class PVAudition extends AuditEntity {

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "audition_id", nullable = false, unique = true)
    private Audition audition;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "drafted_by_id", nullable = false)
    private Agent draftedBy;

    @Column(name = "interviewee_signed", nullable = false)
    @Builder.Default
    private Boolean intervieweeSigned = false;

    @Column(name = "interviewee_signature_refused", nullable = false)
    @Builder.Default
    private Boolean intervieweeSignatureRefused = false;

    @Column(name = "finalized_at")
    private Instant finalizedAt;

    public boolean isFinalized() {
        return finalizedAt != null;
    }

    public void finalizeSignatures(boolean signed, boolean refused) {
        this.intervieweeSigned = signed;
        this.intervieweeSignatureRefused = refused;
        this.finalizedAt = Instant.now();
    }
}
