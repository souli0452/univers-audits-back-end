package gov.bf.ascelc.univers_audits.model.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class FicheRetexResponse {

    private UUID id;
    private UUID investigationId;
    private UUID typeInfractionId;
    private String typeInfractionLibelle;
    private String lieu;
    private String difficultesRencontrees;
    private String origineSoupcons;
    private BigDecimal impactFinancier;
    private String originaliteSchemas;
    private String collaborateursPlanifies;
    private Integer joursCharges;
    private String contexte;
    private String strategieMethodes;
    private String syntheseResultats;
    private String enseignementsAxesAmelioration;
    private String redigeParNom;
    private Instant createdAt;
}
