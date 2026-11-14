package gov.bf.ascelc.univers_audits.shared.utils;

import gov.bf.ascelc.univers_audits.security.permission.AppPermission;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityUtilsPermissionTest {

    private final SecurityUtils securityUtils = new SecurityUtils();

    @AfterEach
    void nettoyer() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void hasPermission_vraiQuandLAutoriteEstPresente() {
        connecter("PERM_DOSSIER_VOIR");
        assertThat(securityUtils.hasPermission(AppPermission.DOSSIER_VOIR)).isTrue();
    }

    @Test
    void hasPermission_fauxPourUneAutrePermissionOuUnRoleSeul() {
        connecter("ROLE_CGE", "PERM_DOSSIER_MODIFIER");
        assertThat(securityUtils.hasPermission(AppPermission.DOSSIER_VOIR)).isFalse();
    }

    @Test
    void hasPermission_fauxSansAuthentification() {
        assertThat(securityUtils.hasPermission(AppPermission.DOSSIER_VOIR)).isFalse();
    }

    private static void connecter(String... autorites) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "u", "n/a", java.util.Arrays.stream(autorites).map(SimpleGrantedAuthority::new).toList()));
    }
}
