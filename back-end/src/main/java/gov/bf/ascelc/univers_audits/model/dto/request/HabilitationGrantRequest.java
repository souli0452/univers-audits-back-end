package gov.bf.ascelc.univers_audits.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HabilitationGrantRequest {

    @NotNull(message = "L'agent est obligatoire")
    private UUID agentId;

    @NotBlank(message = "Le motif est obligatoire pour un octroi manuel")
    @Size(max = 500)
    private String reason;
}
