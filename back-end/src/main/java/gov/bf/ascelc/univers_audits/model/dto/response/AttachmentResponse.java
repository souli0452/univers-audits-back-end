package gov.bf.ascelc.univers_audits.model.dto.response;

import gov.bf.ascelc.univers_audits.enums.AttachmentSource;
import gov.bf.ascelc.univers_audits.enums.AttachmentStatus;
import gov.bf.ascelc.univers_audits.enums.AttachmentType;
import lombok.*;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AttachmentResponse {
    private UUID id;
    private String originalName;
    private String storedName;
    private AttachmentType type;
    private AttachmentSource source;
    private String mimeType;
    private Long fileSizeBytes;
    private AttachmentStatus status;
    private String description;
    private String rejectionReason;
    private String downloadUrl;
    private String thumbnailUrl;
    private Boolean directCapture;
    private Double latitude;
    private Double longitude;
    private LocalDateTime uploadedAt;
    private Instant validatedAt;
}