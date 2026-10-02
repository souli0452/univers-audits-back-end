package gov.bf.ascelc.univers_audits.model.dto.response;

public enum StatutDelaiEtape {
    /** Étape en cours, échéance à plus de 24 h. */
    EN_COURS,
    /** Étape en cours, échéance dans moins de 24 h. */
    PROCHE,
    /** Étape en cours, échéance dépassée. */
    DEPASSE,
    /** Étape terminée dans le délai. */
    RESPECTE,
    /** Étape terminée, mais après l'échéance. */
    TERMINE_EN_RETARD
}
