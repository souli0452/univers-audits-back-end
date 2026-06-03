package gov.bf.ascelc.univers_audits.model.dto.request;

import gov.bf.ascelc.univers_audits.enums.DossierPriority;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.time.Instant;

@Data
public class SetPriorityRequest {

    @NotNull(message = "Le niveau de priorité est obligatoire")
    private DossierPriority priority;

    @Size(min = 5, max = 500, message = "Le motif doit contenir entre 5 et 500 caractères")
    private String reason;

    private Instant deadline;

    @NotNull
    private Long version;
}