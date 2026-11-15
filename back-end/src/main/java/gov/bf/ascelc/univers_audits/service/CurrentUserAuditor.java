package gov.bf.ascelc.univers_audits.service;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Journalise une action au nom de l'utilisateur connecté : identité, rôles métier, adresse IP et navigateur sont
 * lus ici, dans le thread de la requête, puis l'écriture asynchrone est confiée à {@link AuditService}. À utiliser
 * quand le contrôleur n'est pas l'endroit où l'on sait qu'il faut journaliser (règle métier dans un service).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CurrentUserAuditor {

    /** Rôles Keycloak techniques, sans valeur métier : absents du journal. */
    private static final Set<String> ROLES_TECHNIQUES = Set.of("offline_access", "uma_authorization");
    private static final String PREFIXE_ROLE_PAR_DEFAUT = "default-roles-";

    private final AuditService audit;
    private final ProtectedIdentityAlertService alerts;

    public void record(String action, String entityType, String entityId, String description) {
        String acteurId = "SYSTEM";
        String acteurNom = null;
        String roles = null;
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Jwt jwt) {
            acteurId = jwt.getSubject();
            acteurNom = jwt.getClaimAsString("name");
            roles = rolesMetier(jwt);
        }
        String ip = null;
        String userAgent = null;
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            HttpServletRequest request = attrs.getRequest();
            ip = AuditService.extractIp(request);
            userAgent = AuditService.extractUserAgent(request);
        }
        audit.logAction(acteurId, acteurNom, roles, action, entityType, entityId, description, ip, userAgent);
        alerter(acteurId, acteurNom, roles, action, entityId, description);
    }

    /** L'alerte ne doit jamais empêcher l'écriture au journal ni l'action métier : toute panne est seulement logguée. */
    private void alerter(String acteurId, String acteurNom, String roles, String action, String entityId,
                         String description) {
        try {
            alerts.notifyAccess(acteurId, acteurNom, roles, action, entityId, description);
        } catch (RuntimeException e) {
            log.error("[Audit] Alerte sécurité non envoyée pour l'action {} : {}", action, e.getMessage());
        }
    }

    private static String rolesMetier(Jwt jwt) {
        Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
        if (realmAccess == null || !(realmAccess.get("roles") instanceof Collection<?> brut)) {
            return null;
        }
        List<String> metier = brut.stream().map(String::valueOf)
                .filter(r -> !ROLES_TECHNIQUES.contains(r) && !r.startsWith(PREFIXE_ROLE_PAR_DEFAUT))
                .toList();
        return metier.isEmpty() ? null : String.join(",", metier);
    }
}
