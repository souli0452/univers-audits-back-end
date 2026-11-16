package gov.bf.ascelc.univers_audits.shared.utils;

import gov.bf.ascelc.univers_audits.security.permission.AppPermission;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class SecurityUtils {

    public Optional<String> getCurrentKeycloakId() {
        Authentication auth = getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return Optional.empty();
        }

        if (auth.getPrincipal() instanceof Jwt jwt) {
            return Optional.ofNullable(jwt.getSubject());
        }
        return Optional.empty();
    }


    public String getCurrentFirstName() {
        return getClaimAsString("given_name").orElse("Agent");
    }

    public String getCurrentLastName() {
        return getClaimAsString("family_name").orElse("Inconnu");
    }

    public String getCurrentFullName() {
        String first = getCurrentFirstName();
        String last  = getCurrentLastName();

        if (first.isBlank() && last.isBlank()) {
            return "Système";
        }
        return (first + " " + last).trim();
    }


    /** Vrai si l'utilisateur connecté détient cette permission (autorité {@code PERM_xxx} du jeton). */
    public boolean hasPermission(AppPermission permission) {
        Authentication auth = getAuthentication();
        if (auth == null) return false;

        return auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals(permission.authority()));
    }

    private Authentication getAuthentication() {
        return SecurityContextHolder.getContext().getAuthentication();
    }

    private Optional<String> getClaimAsString(String claimName) {
        Authentication auth = getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return Optional.empty();
        }

        if (auth.getPrincipal() instanceof Jwt jwt) {
            Object value = jwt.getClaim(claimName);
            return value != null
                    ? Optional.of(value.toString())
                    : Optional.empty();
        }
        return Optional.empty();
    }
}