package gov.bf.ascelc.univers_audits.enums;

/** Typologie institutionnelle de l'entité visée, telle que publiée dans la situation trimestrielle. */
public enum TypologieInstitutionnelle {
    MINISTERES_ADMINISTRATIONS_CENTRALES("Ministères et administrations centrales"),
    ETABLISSEMENTS_PUBLICS("Établissements publics de l'État et entités assimilées"),
    SOCIETES_D_ETAT("Sociétés d'État et entreprises publiques"),
    PROJETS_PROGRAMMES("Projets, programmes de développement"),
    FONDS_FINANCEMENT("Fonds de financement"),
    COLLECTIVITES_TERRITORIALES("Collectivités territoriales et services déconcentrés"),
    AUTRES("Autres secteurs privés ou transversaux");

    private final String libelle;

    TypologieInstitutionnelle(String libelle) {
        this.libelle = libelle;
    }

    public String libelle() {
        return libelle;
    }
}
