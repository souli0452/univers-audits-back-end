package gov.bf.ascelc.univers_audits.model.dto.response;

import gov.bf.ascelc.univers_audits.enums.EscalationLevel;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DemandeDocumentsResponse {
    private UUID id;
    private UUID investigationId;
    private String recipientLabel;
    private String documentsRequested;
    private String requestedByName;
    private Instant sentAt;
    private Instant deadline;
    private EscalationLevel escalationLevel;
    private Boolean received;
    private Instant receivedAt;
    private Boolean overdue;
}
