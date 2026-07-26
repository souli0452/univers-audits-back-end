package gov.bf.ascelc.univers_audits.enums;

/**
 * Statuts du cycle de vie d'une audition.
 */
public enum AuditionStatus {
    // Planifiée, pas encore tenue
    SCHEDULED,
    // Tenue, compte-rendu enregistré
    CONDUCTED,
    // Annulée avant d'avoir eu lieu
    CANCELLED,
    // La personne convoquée ne s'est pas présentée
    NO_SHOW
}
