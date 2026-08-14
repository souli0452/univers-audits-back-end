package gov.bf.ascelc.univers_audits.model.dto.response;

import gov.bf.ascelc.univers_audits.enums.AttachmentSource;
import gov.bf.ascelc.univers_audits.enums.AttachmentStatus;
import gov.bf.ascelc.univers_audits.enums.ModeObtention;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventairePieceItemResponse {
    private UUID attachmentId;
    private String code;
    private String description;
    private AttachmentSource source;
    private LocalDateTime uploadedAt;
    private ModeObtention modeObtention;
    private AttachmentStatus status;
}
