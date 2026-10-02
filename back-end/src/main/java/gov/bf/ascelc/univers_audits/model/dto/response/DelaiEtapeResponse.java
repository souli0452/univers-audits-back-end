package gov.bf.ascelc.univers_audits.model.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Délai d'une étape du circuit de traitement (workflow PGPD_GU V3) pour un dossier donné.
 * Calculé à la volée à partir des dates déjà enregistrées : rien n'est stocké.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DelaiEtapeResponse {

    /** Code stable de l'étape, identique au code du paramètre de délai qui la pilote. */
    private String code;
    private String libelle;
    /** Acteur chargé de l'étape (ex. « CGEA »). */
    private String acteur;
    /** Début de l'étape ; l'étape n'est listée que si elle a commencé. */
    private Instant debut;
    /** Échéance : début + délai en jours ouvrables, à la même heure. */
    private Instant echeance;
    /** Fin de l'étape, absente tant qu'elle est en cours. */
    private Instant fin;
    private int delaiJours;
    private boolean joursOuvrables;
    private StatutDelaiEtape statut;
    /** Heures restantes avant l'échéance (négatif si dépassée) ; absent quand l'étape est terminée. */
    private Long heuresRestantes;
}
