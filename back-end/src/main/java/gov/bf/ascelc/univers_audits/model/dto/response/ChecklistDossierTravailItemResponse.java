package gov.bf.ascelc.univers_audits.model.dto.response;

import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChecklistDossierTravailItemResponse {
    private UUID pointId;
    private String code;
    private String libelle;
    private String categorie;
    private Integer ordre;
    private boolean coche;
    private String cocheParNom;
    private Instant cocheAt;
    private String commentaire;
}
