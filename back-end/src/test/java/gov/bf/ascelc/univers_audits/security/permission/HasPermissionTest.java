package gov.bf.ascelc.univers_audits.security.permission;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringJUnitConfig(HasPermissionTest.Config.class)
class HasPermissionTest {

    @Configuration
    @EnableMethodSecurity
    @Import(MethodSecurityConfig.class)
    static class Config {
        @Bean
        Cible cible() {
            return new Cible();
        }
    }

    public static class Cible {
        @HasPermission(AppPermission.DOSSIER_VOIR)
        public String voir() {
            return "ok";
        }
    }

    @org.springframework.beans.factory.annotation.Autowired
    private Cible cible;

    @Test
    @WithMockUser(authorities = "PERM_DOSSIER_VOIR")
    void laPermissionDemandeeOuvreLAcces() {
        assertThat(cible.voir()).isEqualTo("ok");
    }

    @Test
    @WithMockUser(authorities = "PERM_DOSSIER_MODIFIER")
    void uneAutrePermissionNOuvrePasLAcces() {
        assertThatThrownBy(() -> cible.voir()).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @WithMockUser(roles = "ADMIN_DDIC")
    void unRoleSeulNOuvrePlusLAcces() {
        assertThatThrownBy(() -> cible.voir()).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @WithAnonymousUser
    void unAnonymeEstRefuse() {
        assertThatThrownBy(() -> cible.voir()).isInstanceOf(AccessDeniedException.class);
    }
}
