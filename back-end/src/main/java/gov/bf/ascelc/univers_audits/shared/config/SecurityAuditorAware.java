package gov.bf.ascelc.univers_audits.shared.config;

import org.springframework.data.domain.AuditorAware;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.Optional;


@Component("securityAuditorAware")
public class SecurityAuditorAware implements AuditorAware<String> {

    @Override
    public Optional<String> getCurrentAuditor() {
        Authentication auth = SecurityContextHolder
                .getContext()
                .getAuthentication();


        if (auth == null || !auth.isAuthenticated()) {
            return Optional.of("system");
        }


        if (auth.getPrincipal() instanceof Jwt jwt) {
            return Optional.ofNullable(jwt.getSubject())
                    .or(() -> Optional.of("system")); // Fallback si sub manquant
        }

        return Optional.ofNullable(auth.getName())
                .or(() -> Optional.of("system"));
    }
}