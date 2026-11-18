package gov.bf.ascelc.univers_audits.shared.utils;

import gov.bf.ascelc.univers_audits.model.entity.Declarant;
import gov.bf.ascelc.univers_audits.model.entity.Dossier;

/**
 * Minimisation de l'adresse IP des citoyens.
 *
 * <p>Pour un dépôt anonyme ou placé sous protection de lanceur d'alerte, l'adresse de connexion ne doit être
 * conservée nulle part (historique du dossier, journal d'audit, journaux du serveur) : elle permettrait, à qui
 * accède à la base ou aux journaux, de remonter jusqu'à la personne. Pour un dépôt identifié sans protection,
 * elle reste utile à la traçabilité (abus, usurpation).
 */
public final class ProtectionAdresseIp {

    private ProtectionAdresseIp() {
    }

    /** L'adresse à enregistrer pour ce citoyen : {@code null} s'il est anonyme ou sous protection. */
    public static String aConserver(boolean anonyme, boolean protection, String adresseIp) {
        return anonyme || protection ? null : adresseIp;
    }

    /** Même règle, d'après un dossier déjà enregistré (réponse à une demande de complément, par exemple). */
    public static String aConserver(Dossier dossier, String adresseIp) {
        boolean anonyme = Boolean.TRUE.equals(dossier.getAnonymous());
        Declarant declarant = dossier.getDeclarant();
        boolean protection = declarant != null && Boolean.TRUE.equals(declarant.getProtectionRequested());
        return aConserver(anonyme, protection, adresseIp);
    }
}
