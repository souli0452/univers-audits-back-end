package gov.bf.ascelc.univers_audits.model.dto.request;

import gov.bf.ascelc.univers_audits.enums.EtatAvancementAffectation;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FicheAffectationSuiviRequest {

    @NotNull
    private EtatAvancementAffectation etatAvancement;

    /** Requis si etatAvancement = AUTRE. */
    private String etatAvancementPrecision;

    private String commentairesSuivi;
}
