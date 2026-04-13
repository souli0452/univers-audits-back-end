package gov.bf.ascelc.univers_audits.enums;

public enum TypeDeclarant {

    // Personne physique burkinabè ou étrangère qui s'identifie
    CITIZEN,

    // Société, entreprise privée ou publique
    COMPANY,

    // ONG, association de la société civile, groupement citoyen
    ASSOCIATION,

    // Institution partenaire, administration publique,
    // bailleur de fonds qui transmet un signalement
    PUBLIC_AUTHORITY,

    // Le déclarant ne révèle pas son identité.
    // firstName, lastName, email, phone = null en base.
    // Protégé par la loi N°010-2004/AN.
    ANONYMOUS,

    // L'ASCE-LC se saisit elle-même après veille médiatique
    // ou réception d'un rapport d'audit ou d'inspection.
    // Pas de déclarant externe dans ce cas.
    ASCE_SELF_REFERRAL
}