package gov.bf.ascelc.univers_audits.model.dto.response;

import gov.bf.ascelc.univers_audits.enums.AutoReferralSource;
import gov.bf.ascelc.univers_audits.enums.StatutInformationPreoccupante;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class InformationPreoccupanteResponse {

    private UUID id;
    private String objet;
    private String description;
    private AutoReferralSource source;
    private String sourceReference;
    private Instant dateReception;
    private StatutInformationPreoccupante statut;
    private Instant createdAt;
    private List<DossierRattacheResponse> dossiersRattaches;

    @Data
    @Builder
    public static class DossierRattacheResponse {
        private UUID dossierId;
        private String dossierNumber;
        private String commentaire;
        private Instant linkedAt;
    }
}
