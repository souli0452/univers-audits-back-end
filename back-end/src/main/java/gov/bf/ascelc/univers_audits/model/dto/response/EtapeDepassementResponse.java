package gov.bf.ascelc.univers_audits.model.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

/** Étape du circuit de traitement dont l'échéance est dépassée, pour un dossier. */
@Data
@Builder
public class EtapeDepassementResponse {
    private UUID dossierId;
    private String numero;
    private String code;
    private String libelle;
    private Instant echeance;
    /** Retard en heures, toujours positif. */
    private long heuresDeRetard;
}
