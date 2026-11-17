package gov.bf.ascelc.univers_audits.enums;

/** Origine géographique d'une saisine, telle que publiée dans la situation trimestrielle. */
public enum RegionOrigine {
    KADIOGO("Région du Kadiogo"),
    GUIRIKO("Région du Guiriko"),
    AUTRES_REGIONS("Autres régions et communes"),
    DIASPORA("Diaspora / Extérieur du pays");

    private final String libelle;

    RegionOrigine(String libelle) {
        this.libelle = libelle;
    }

    public String libelle() {
        return libelle;
    }
}
