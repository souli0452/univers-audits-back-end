package gov.bf.ascelc.univers_audits.controller;

import gov.bf.ascelc.univers_audits.service.KeycloakAdminService;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Un agent ne modifie plus rien de son compte par la plateforme (guide ANSSI, R23 ; audit S-17) : le mot de passe se
 * change sur la page de Keycloak, le nom et le courriel par l'administrateur DDIC.
 */
class ProfileControllerMotDePasseTest {

    @Test
    void laPlateformeNAPlusDeRouteDeProfil() {
        assertThatThrownBy(() -> Class.forName("gov.bf.ascelc.univers_audits.controller.ProfileController"))
                .isInstanceOf(ClassNotFoundException.class);
    }

    @Test
    void leServiceKeycloakNeManipuleAucunMotDePasseSaisiParUnAgent() {
        assertThat(Arrays.stream(KeycloakAdminService.class.getDeclaredMethods()).map(java.lang.reflect.Method::getName))
                .doesNotContain("verifyCurrentPassword", "changePassword");
    }
}
