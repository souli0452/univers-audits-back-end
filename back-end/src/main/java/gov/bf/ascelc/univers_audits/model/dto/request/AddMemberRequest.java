package gov.bf.ascelc.univers_audits.model.dto.request;

import gov.bf.ascelc.univers_audits.enums.TeamRole;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AddMemberRequest {

    @NotNull(message = "L'ID de l'agent est obligatoire")
    private UUID agentId;

    @NotNull(message = "Le rôle dans l'équipe est obligatoire")
    private TeamRole teamRole;
}