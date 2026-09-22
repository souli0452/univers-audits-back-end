package gov.bf.ascelc.univers_audits.model.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
@Builder
public class ActeurDepassementResponse {

    private UUID agentId;
    private String matricule;
    private String nomComplet;
    private String departementLibelle;
    private List<DepassementItemResponse> dossiersEnDepassement;
}
