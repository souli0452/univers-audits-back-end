package gov.bf.ascelc.univers_audits.enums;

public enum HabilitationSource {
    AGENT_IN_CHARGE,
    INVESTIGATION_TEAM,
    MANUAL,
    /** Imputation d'un dossier à un département (le chef) puis répartition à ses agents : se retire à la réaffectation. */
    AFFECTATION
}
