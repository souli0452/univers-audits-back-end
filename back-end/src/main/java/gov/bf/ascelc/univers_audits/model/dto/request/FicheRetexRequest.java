package gov.bf.ascelc.univers_audits.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FicheRetexRequest {

    private UUID typeInfractionId;

    @Size(max = 300, message = "Le lieu ne doit pas dépasser 300 caractères")
    private String lieu;

    private String difficultesRencontrees;

    private String origineSoupcons;

    private BigDecimal impactFinancier;

    private String originaliteSchemas;

    private String collaborateursPlanifies;

    private Integer joursCharges;

    private String contexte;

    private String strategieMethodes;

    @NotBlank(message = "La synthèse des résultats est obligatoire")
    private String syntheseResultats;

    @NotBlank(message = "Les enseignements et axes d'amélioration sont obligatoires")
    private String enseignementsAxesAmelioration;
}
