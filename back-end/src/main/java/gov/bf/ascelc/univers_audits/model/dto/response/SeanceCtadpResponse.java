package gov.bf.ascelc.univers_audits.model.dto.response;

import gov.bf.ascelc.univers_audits.enums.StatutSeanceCtadp;
import lombok.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SeanceCtadpResponse {

    private UUID id;
    private Instant dateSeance;
    private StatutSeanceCtadp statut;
    private String participants;
    private String procesVerbal;
    private List<SeanceCtadpDossierResponse> dossiers;
}
