package gov.bf.ascelc.univers_audits.model.dto.request;

import jakarta.validation.constraints.NotEmpty;
import lombok.*;

import java.util.List;
import java.util.UUID;

/** Agents du département auxquels le chef répartit le dossier. */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FicheAffectationRepartitionRequest {

    @NotEmpty
    private List<UUID> agentIds;
}
