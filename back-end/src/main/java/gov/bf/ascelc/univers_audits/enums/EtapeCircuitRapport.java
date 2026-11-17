package gov.bf.ascelc.univers_audits.enums;

/**
 * Étapes du circuit d'approbation du rapport d'enquête, dans l'ordre du workflow PGPD_GU V3 (étapes 21 à 23) :
 * conseiller juridique, chef du DEI (« C.DEI »), CGEA, CGE. Chaque étape porte le rôle de la personne qui décide.
 */
public enum EtapeCircuitRapport {
    JURIDIQUE("CONSEILLER_JURIDIQUE", "conseiller juridique"),
    DEI("CHEF_DEI", "chef du DEI"),
    CGEA("CGEA", "CGEA"),
    CGE("CGE", "CGE");

    private final String roleKey;
    private final String libelle;

    EtapeCircuitRapport(String roleKey, String libelle) {
        this.roleKey = roleKey;
        this.libelle = libelle;
    }

    public String roleKey() {
        return roleKey;
    }

    public String libelle() {
        return libelle;
    }
}
