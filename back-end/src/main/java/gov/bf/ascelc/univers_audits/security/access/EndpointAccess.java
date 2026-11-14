package gov.bf.ascelc.univers_audits.security.access;

import java.util.Optional;
import java.util.SortedSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Contrôle d'accès d'un point d'entrée de l'API, tel qu'il est écrit dans le code.
 *
 * @param handler     {@code Controleur#methode(TypesDesParametres)}
 * @param httpMethods méthodes HTTP triées et séparées par des virgules ; vide = toutes
 * @param kind        nature du contrôle
 * @param roles       rôles cités par l'expression (vide hors {@link Kind#ROLES})
 * @param expression  expression {@code @PreAuthorize} d'origine ; {@code null} si le point d'entrée n'en a pas
 */
public record EndpointAccess(String handler, String httpMethods, Kind kind, SortedSet<String> roles,
                             String expression) {

    private static final Pattern PERMISSION = Pattern.compile("hasAuthority\\('PERM_([A-Z0-9_]+)'\\)");

    public enum Kind {
        /** Aucun {@code @PreAuthorize} : public ou protégé uniquement par la configuration d'URL. */
        NONE,
        /** Tout utilisateur connecté. */
        AUTHENTICATED,
        /** Liste de rôles. */
        ROLES,
        /** Permission portée par {@code @HasPermission}. */
        PERMISSION,
        /** Règle qui ne se ramène ni à un rôle ni à une permission (habilitation sur le dossier, etc.). */
        SPECIFIC
    }

    /** Nom du contrôleur sans le suffixe « Controller » (« SeanceCtadp » pour {@code SeanceCtadpController}). */
    public String module() {
        int diese = handler.indexOf('#');
        String controleur = diese < 0 ? handler : handler.substring(0, diese);
        return controleur.replaceAll("Controller$", "");
    }

    /** Consultation : GET, HEAD, ou toutes méthodes. */
    public boolean lecture() {
        return httpMethods.isEmpty() || httpMethods.contains("GET") || httpMethods.contains("HEAD");
    }

    /** Clé de permission (sans le préfixe {@code PERM_}) citée par l'expression, s'il y en a une. */
    public Optional<String> permission() {
        if (expression == null) {
            return Optional.empty();
        }
        Matcher m = PERMISSION.matcher(expression);
        return m.find() ? Optional.of(m.group(1)) : Optional.empty();
    }
}
