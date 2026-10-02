package gov.bf.ascelc.univers_audits.model.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/** Étapes en retard regroupées par acteur du circuit (CGEA, CGE), du retard le plus grand au plus petit. */
@Data
@Builder
public class ActeurEtapeDepassementResponse {
    private String acteur;
    private List<EtapeDepassementResponse> etapes;
}
