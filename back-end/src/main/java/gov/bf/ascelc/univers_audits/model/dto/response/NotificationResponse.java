package gov.bf.ascelc.univers_audits.model.dto.response;

import gov.bf.ascelc.univers_audits.enums.NotificationChannel;
import gov.bf.ascelc.univers_audits.enums.NotificationStatus;
import gov.bf.ascelc.univers_audits.enums.NotificationType;
import lombok.*;
import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationResponse {
    private UUID id;
    private NotificationType    type;
    private NotificationChannel channel;
    private String              subject;
    private NotificationStatus  status;
    private String              formReference;
    private Instant             scheduledAt;
    private Instant             sentAt;
    private Boolean             overdue;
    private Integer             retryCount;
    private Instant             readAt;

    private String  content;
    private String  dossierId;
    private String  dossierNumber;
}