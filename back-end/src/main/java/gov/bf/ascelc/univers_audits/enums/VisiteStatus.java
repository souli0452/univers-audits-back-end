package gov.bf.ascelc.univers_audits.enums;

/**
 * Statuts du cycle de vie d'une visite terrain.
 */
public enum VisiteStatus {
    // Planifiée, pas encore tenue
    SCHEDULED,
    // Tenue, constat enregistré
    CONDUCTED,
    // Annulée avant d'avoir eu lieu
    CANCELLED,
    // Tentée mais n'a pas pu aboutir (site inaccessible, accès refusé...)
    CARENCE
}
