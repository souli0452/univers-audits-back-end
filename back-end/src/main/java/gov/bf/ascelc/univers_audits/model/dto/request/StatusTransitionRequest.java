package gov.bf.ascelc.univers_audits.model.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StatusTransitionRequest {

    @NotNull(message = "La version est obligatoire")
    private Long version;

    @Size(max = 2000, message = "Le motif ne doit pas dépasser 2000 caractères")
    private String reason;

    @Size(max = 300)
    private String transferInstitution;
}