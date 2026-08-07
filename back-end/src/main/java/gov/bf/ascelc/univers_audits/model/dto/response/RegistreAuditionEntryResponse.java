package gov.bf.ascelc.univers_audits.model.dto.response;

import gov.bf.ascelc.univers_audits.enums.AuditionStatus;
import gov.bf.ascelc.univers_audits.enums.IntervieweeType;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegistreAuditionEntryResponse {
    private UUID auditionId;
    private UUID investigationId;
    private String dossierNumber;
    private IntervieweeType intervieweeType;
    private String intervieweeDisplayName;
    private Instant scheduledAt;
    private AuditionStatus status;
    private PvStatus pvStatus;
    private Integer pvVersion;

    public enum PvStatus {
        AUCUN_PV, BROUILLON, FINALISE
    }
}
