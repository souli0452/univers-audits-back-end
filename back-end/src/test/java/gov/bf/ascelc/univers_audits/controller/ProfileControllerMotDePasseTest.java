package gov.bf.ascelc.univers_audits.controller;

import gov.bf.ascelc.univers_audits.service.KeycloakAdminService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Le mot de passe se change uniquement sur la page de Keycloak (audit du 2026-11-24, S-17) : la plateforme ne reçoit,
 * ne vérifie et ne transmet plus aucun mot de passe d'agent.
 */
class ProfileControllerMotDePasseTest {

    @Test
    void laPlateformeNAAucuneRoutePourChangerUnMotDePasse() throws Exception {
        MockMvc mvc = MockMvcBuilders
                .standaloneSetup(ProfileController.class.getDeclaredConstructors()[0].newInstance(arguments()))
                .build();

        mvc.perform(put("/api/v1/profile/password").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void leServiceKeycloakNeManipuleAucunMotDePasseSaisiParUnAgent() {
        assertThat(Arrays.stream(KeycloakAdminService.class.getDeclaredMethods()).map(java.lang.reflect.Method::getName))
                .doesNotContain("verifyCurrentPassword", "changePassword");
    }

    private static Object[] arguments() {
        return Arrays.stream(ProfileController.class.getDeclaredConstructors()[0].getParameterTypes())
                .map(Mockito::mock).toArray();
    }
}
