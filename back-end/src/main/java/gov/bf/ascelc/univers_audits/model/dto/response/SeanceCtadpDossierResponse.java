package gov.bf.ascelc.univers_audits.model.dto.response;

import gov.bf.ascelc.univers_audits.enums.RecommandationCtadp;
import lombok.*;

import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SeanceCtadpDossierResponse {

    private UUID id;
    private UUID dossierId;
    private String dossierNumber;
    private String dossierObject;
    private RecommandationCtadp recommandation;
    private String commentaire;
}
