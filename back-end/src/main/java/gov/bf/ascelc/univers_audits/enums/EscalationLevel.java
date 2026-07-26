package gov.bf.ascelc.univers_audits.enums;

/**
 * Niveau d'escalade d'une demande de documents non satisfaite.
 */
public enum EscalationLevel {
    // Demande initiale envoyée
    INITIAL,
    // Relance après dépassement du premier délai
    RELANCE,
    // Sommation après dépassement du délai de relance
    SOMMATION,
    // Saisine judiciaire après dépassement du délai de sommation
    SAISINE_JUDICIAIRE
}
