package gov.bf.ascelc.univers_audits.service;

import gov.bf.ascelc.univers_audits.model.dto.response.DeclarantResponse;
import gov.bf.ascelc.univers_audits.model.dto.response.DossierResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Journalise l'export d'un document (PDF) à partir d'un dossier dont l'identité du lanceur d'alerte protégé est
 * visible pour l'utilisateur connecté : c'est le moment où l'identité quitte l'application. L'ouverture du dossier
 * qui précède l'export est déjà tracée à part.
 */
@Component
@RequiredArgsConstructor
public class ProtectedIdentityExportAuditor {

    private final CurrentUserAuditor auditor;

    /**
     * @param dossier  dossier tel que renvoyé à l'utilisateur (donc déjà masqué s'il n'est pas habilité)
     * @param document nom lisible du document exporté
     */
    public void record(DossierResponse dossier, String document) {
        DeclarantResponse declarant = dossier.getDeclarant();
        if (declarant == null
                || !Boolean.TRUE.equals(declarant.getProtectionRequested())
                || !identiteEnClair(declarant)) {
            return;
        }
        auditor.record("EXPORTER_IDENTITE_LANCEUR_ALERTE", "DOSSIER", String.valueOf(dossier.getId()),
                "Export PDF « " + document + " » du dossier "
                        + (dossier.getNumber() != null ? dossier.getNumber() : dossier.getId())
                        + " — identité du lanceur d'alerte protégé visible par l'exportateur");
    }

    /**
     * Vrai si la réponse porte encore une donnée qui identifie le déclarant (nom, organisation, courriel, téléphone) :
     * c'est-à-dire qu'elle n'a pas été masquée pour l'utilisateur connecté.
     */
    public static boolean identiteEnClair(DeclarantResponse declarant) {
        return declarant != null
                && (notBlank(declarant.getFirstName()) || notBlank(declarant.getLastName())
                || notBlank(declarant.getOrganizationName()) || notBlank(declarant.getEmail())
                || notBlank(declarant.getPhoneNumber()));
    }

    private static boolean notBlank(String valeur) {
        return valeur != null && !valeur.isBlank();
    }
}
