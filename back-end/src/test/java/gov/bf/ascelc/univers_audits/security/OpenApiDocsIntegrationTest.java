package gov.bf.ascelc.univers_audits.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * La définition de l'API (OpenAPI) sert aux outils de test de sécurité (OWASP ZAP) et à la documentation en développement.
 * Elle est coupée en production (application-prod.properties). Cet essai attrape une bibliothèque springdoc trop ancienne
 * pour la version de Spring : le 2026-11-24, springdoc 2.6.0 avec Spring Boot 3.5 donnait « NoSuchMethodError … ControllerAdviceBean ».
 */
@SpringBootTest
@AutoConfigureMockMvc
class OpenApiDocsIntegrationTest {

    @Autowired private MockMvc mvc;

    @Test
    void laDefinitionDeLApiEstGeneree() throws Exception {
        MvcResult r = mvc.perform(get("/v3/api-docs")).andReturn();

        assertThat(r.getResponse().getStatus()).isEqualTo(200);
        assertThat(r.getResponse().getContentAsString()).contains("\"openapi\"").contains("/api/v1/dossiers");
    }
}
