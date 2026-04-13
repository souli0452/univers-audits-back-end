package gov.bf.ascelc.univers_audits.shared.utils;

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


    public boolean hasRole(String role) {
        Authentication auth = getAuthentication();
        if (auth == null) return false;

        return auth.getAuthorities().stream()
                .anyMatch(a -> {
                    String authority = a.getAuthority();
                    return authority.equals("ROLE_" + role)
                            || authority.equals(role);
                });
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