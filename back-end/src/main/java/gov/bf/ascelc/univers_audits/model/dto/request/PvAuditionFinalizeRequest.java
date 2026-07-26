package gov.bf.ascelc.univers_audits.model.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PvAuditionFinalizeRequest {

    @NotNull(message = "L'indicateur de signature est obligatoire")
    private Boolean intervieweeSigned;

    @NotNull(message = "L'indicateur de refus de signature est obligatoire")
    private Boolean intervieweeSignatureRefused;
}
