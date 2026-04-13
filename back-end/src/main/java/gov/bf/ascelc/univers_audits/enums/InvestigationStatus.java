package gov.bf.ascelc.univers_audits.enums;

/**
 * Statuts du cycle de vie d'une investigation.
 */
public enum InvestigationStatus {
    // Créée après accord du CGE — équipe pas encore constituée
    INITIATED,
    // Enquête terrain en cours
    IN_PROGRESS,
    // Temporairement suspendue (motif obligatoire)
    SUSPENDED,
    // Rapport final remis — en circuit d'approbation
    COMPLETED,
    // Archivée après décision finale du CGE
    ARCHIVED
}