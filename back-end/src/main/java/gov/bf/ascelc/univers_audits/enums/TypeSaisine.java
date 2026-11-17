package gov.bf.ascelc.univers_audits.enums;

public enum TypeSaisine {
    DENONCIATION,
    PLAINTE,
    SIGNALEMENT,
    AUTO_SAISINE,
    /** Un agent qui enregistre peut qualifier une saisine à la fois de plainte et de dénonciation. */
    PLAINTE_DENONCIATION,
    /** Demande d'intervention : on demande à l'ASCE-LC une enquête ou un audit. Qualifiée par un agent. */
    DEMANDE_INTERVENTION
}
