package gov.bf.ascelc.univers_audits.model.dto.request;

import gov.bf.ascelc.univers_audits.enums.IntervieweeType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditionScheduleRequest {

    @NotNull(message = "Le type de personne auditionnée est obligatoire")
    private IntervieweeType intervieweeType;

    private UUID targetedPartyId;

    private UUID witnessId;

    @NotNull(message = "La date de convocation est obligatoire")
    private Instant scheduledAt;

    @Size(max = 300)
    private String location;
}
