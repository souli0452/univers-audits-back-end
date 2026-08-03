package gov.bf.ascelc.univers_audits.model.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AddDossierToSeanceRequest {

    @NotNull(message = "L'ID du dossier est obligatoire")
    private UUID dossierId;
}
