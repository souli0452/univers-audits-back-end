package gov.bf.ascelc.univers_audits.shared.utils;

import gov.bf.ascelc.univers_audits.enums.QualiteDeclarant;
import gov.bf.ascelc.univers_audits.enums.TypeDeclarant;
import gov.bf.ascelc.univers_audits.enums.TypeSaisine;
import gov.bf.ascelc.univers_audits.shared.exceptions.BusinessException;
import org.springframework.stereotype.Component;

/**
 * Dérive TypeSaisine (nature de la saisine) depuis le type et la qualité du
 * déclarant, conformément au §4.1 du manuel ASCE-LC. Le client ne choisit
 * jamais directement la nature — elle est toujours calculée ici.
 *
 * <p>Une victime ou son représentant identifiés déposent une plainte ; sans identité (ou en tant que
 * témoin), le signalement est une dénonciation.
 */
@Component
public class NatureSaisineResolver {

    public TypeSaisine resolve(TypeDeclarant typeDeclarant,
                                QualiteDeclarant quality,
                                boolean anonymous) {
        if (typeDeclarant == null) {
            throw new BusinessException(
                    "Le type de déclarant est requis pour déterminer "
                            + "la nature de la saisine.");
        }

        return switch (typeDeclarant) {
            case PUBLIC_AUTHORITY -> TypeSaisine.SIGNALEMENT;
            case ASCE_SELF_REFERRAL -> TypeSaisine.AUTO_SAISINE;
            case CITIZEN, COMPANY, ASSOCIATION, ANONYMOUS ->
                    resolveFromQuality(typeDeclarant, quality, anonymous);
        };
    }

    private TypeSaisine resolveFromQuality(TypeDeclarant typeDeclarant,
                                            QualiteDeclarant quality,
                                            boolean anonymous) {
        if (quality == null) {
            throw new BusinessException(
                    "La qualité du déposant (victime, représentant de la "
                            + "victime ou témoin) est obligatoire pour ce "
                            + "type de déclarant.");
        }

        // Sans identité il n'y a pas de plaignant : le manuel des procédures admet pourtant
        // les signalements anonymes. Une victime qui souhaite rester anonyme n'est donc pas
        // refusée, son signalement est traité comme une dénonciation (la qualité déclarée
        // est conservée telle quelle sur le dossier).
        if (anonymous || typeDeclarant == TypeDeclarant.ANONYMOUS) {
            return TypeSaisine.DENONCIATION;
        }

        return switch (quality) {
            case VICTIME, REPRESENTANT_VICTIME -> TypeSaisine.PLAINTE;
            case TEMOIN -> TypeSaisine.DENONCIATION;
        };
    }
}
