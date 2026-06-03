package gov.bf.ascelc.univers_audits.security;

import gov.bf.ascelc.univers_audits.service.AuditService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class LoginAuditFilter extends OncePerRequestFilter {

    private final AuditService auditService;


    private final Set<String> seenJtis = ConcurrentHashMap.newKeySet();


    private static final int MAX_CACHE_SIZE = 10_000;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();

            if (auth != null && auth.isAuthenticated()
                    && auth.getPrincipal() instanceof Jwt jwt) {

                String jti = jwt.getId();
                if (jti != null && !seenJtis.contains(jti)) {

                    if (seenJtis.size() >= MAX_CACHE_SIZE) {
                        seenJtis.clear();
                    }

                    seenJtis.add(jti);

                    String agentId   = jwt.getSubject();
                    String agentName = jwt.getClaimAsString("name");
                    if (agentName == null || agentName.isBlank()) {
                        agentName = jwt.getClaimAsString("preferred_username");
                    }

                    auditService.logLogin(agentId, agentName, true, null, request);
                    log.debug("[LoginAudit] Connexion enregistrée — agent: {}", agentName);
                }
            }
        } catch (Exception e) {
            log.error("[LoginAudit] Erreur filtre : {}", e.getMessage());
        }

        filterChain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.contains("/public/")
                || path.contains("/actuator/")
                || path.contains("/swagger-ui")
                || path.contains("/api-docs");
    }
}