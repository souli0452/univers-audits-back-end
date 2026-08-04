package gov.bf.ascelc.univers_audits.enums;

/**
 * Rôle d'un agent au sein d'une équipe d'investigation.
 */
public enum TeamRole {
    // Coordonne l'équipe, signataire du rapport final — exactement 1 par équipe
    CHEF_MISSION,
    // Participe à l'enquête terrain — au moins 2 par équipe
    INVESTIGATEUR,
    // Apporte une expertise ponctuelle — nombre libre (0 ou plus)
    PERSONNE_RESSOURCE,
    // Garantit la conformité juridique de la procédure — exactement 1 par équipe
    CONSEIL_JURIDIQUE
}
