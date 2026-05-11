package gov.bf.ascelc.univers_audits.model.entity;

import gov.bf.ascelc.univers_audits.abstracts.AuditEntity;
import gov.bf.ascelc.univers_audits.enums.AttachmentSource;
import gov.bf.ascelc.univers_audits.enums.AttachmentStatus;
import gov.bf.ascelc.univers_audits.enums.AttachmentType;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.time.Instant;
import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "attachment", indexes = {
        @Index(name = "idx_attachment_case",
                columnList = "case_id"),
        @Index(name = "idx_attachment_investigation",
                columnList = "investigation_id"),
        @Index(name = "idx_attachment_status",
                columnList = "status")
})
public class Attachment extends AuditEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "case_id")
    private Dossier dossier;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "investigation_id")
    private Investigation investigation;




    @Column(name = "original_name", nullable = false, length = 255)
    private String originalName;

    @Column(name = "stored_name", nullable = false, length = 255)
    private String storedName;

    @Column(name = "file_path", nullable = false, length = 500)
    private String filePath;

    @Column(name = "mime_type", nullable = false, length = 100)
    private String mimeType;

    @Column(name = "file_size_bytes", nullable = false)
    private Long fileSizeBytes;

    @Column(name = "uploaded_at", nullable = false)
    private LocalDateTime uploadedAt;


    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private AttachmentType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 25)
    private AttachmentSource source;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 25)
    @Builder.Default
    private AttachmentStatus status = AttachmentStatus.PENDING_VALIDATION;


    /** Empreinte SHA-256 pour détecter les doublons et les altérations */
    @Column(name = "hash_sha256", length = 64)
    private String hashSha256;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;

    @Column(name = "thumbnail_path", length = 500)
    private String thumbnailPath;

    @Column(name = "direct_capture")
    private Boolean directCapture;

    @Column(name = "latitude")
    private Double latitude;

    @Column(name = "longitude")
    private Double longitude;


    @Column(name = "validated_at")
    private Instant validatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "validated_by_id")
    private Agent validatedBy;


    public void validate(Agent agent) {
        this.status = AttachmentStatus.VALIDATED;
        this.validatedBy = agent;
        this.validatedAt = Instant.now();
    }

    public void reject(Agent agent, String reason) {
        this.status = AttachmentStatus.REJECTED;
        this.validatedBy = agent;
        this.rejectionReason = reason;
        this.validatedAt = Instant.now();
    }

    public boolean isImage() {
        return mimeType != null && mimeType.startsWith("image/");
    }

    public boolean hasGpsCoordinates() {
        return latitude != null && longitude != null;
    }
}