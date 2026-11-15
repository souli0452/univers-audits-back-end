package gov.bf.ascelc.univers_audits.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Permissions de l'utilisateur connecté, pour que l'interface affiche menus et boutons selon ce que la base lui
 * accorde, sans connaître le nom des rôles. L'interface n'est qu'un confort : chaque requête reste contrôlée ici.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthPermissionsController {

    private static final String PREFIXE = "PERM_";

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/permissions")
    public ResponseEntity<Map<String, List<String>>> permissions(Authentication authentication) {
        List<String> permissions = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(a -> a.startsWith(PREFIXE))
                .map(a -> a.substring(PREFIXE.length()))
                .distinct()
                .sorted()
                .toList();
        return ResponseEntity.ok(Map.of("permissions", permissions));
    }
}
