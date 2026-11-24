package gov.bf.ascelc.univers_audits.shared;

/** Adresse de l'espace des agents (/rpd) : un seul endroit pour tous les liens envoyés par le serveur. */
public final class EspaceAgent {

    private EspaceAgent() {
    }

    /** « https://site » + « /dossiers/1 » donne « https://site/#/rpd/dossiers/1 ». */
    public static String url(String frontendUrl, String chemin) {
        String site = frontendUrl == null ? "" : frontendUrl.replaceAll("/+$", "");
        return site + "/#/rpd" + chemin;
    }
}
