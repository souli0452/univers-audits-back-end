package gov.bf.ascelc.univers_audits.security.permission;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/** {@code GET /api/v1/auth/permissions} : les permissions de l'utilisateur connecté, pour adapter l'interface. */
@SpringBootTest
@AutoConfigureMockMvc
class AuthPermissionsIntegrationTest {

    private static final String URL = "/api/v1/auth/permissions";

    @Autowired private MockMvc mvc;
    @Autowired private KeycloakAuthoritiesConverter authorities;
    @Autowired private PermissionResolver resolver;

    @Test
    void sansJetonLaRequeteEstRefusee() throws Exception {
        assertThat(mvc.perform(get(URL)).andReturn().getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    void renvoieLUnionDesPermissionsDesRolesDuJeton_sansPrefixe() throws Exception {
        List<String> recues = permissions("CGE", "DCP");

        assertThat(recues).contains("DOSSIER_VOIR", "STATISTIQUE_EXPORTER", "DOSSIER_VOIR_TOUS");
        assertThat(recues).noneMatch(p -> p.startsWith("PERM_") || p.startsWith("ROLE_"));
        assertThat(recues).isSorted();
        assertThat(recues).doesNotHaveDuplicates();
    }

    @Test
    void renvoieExactementCeQueLaBaseAccordeAuxRoles() throws Exception {
        List<String> attendues = resolver.resolve(List.of("AGENT_BRPD")).stream()
                .map(a -> a.substring("PERM_".length())).sorted().toList();

        assertThat(permissions("AGENT_BRPD")).isEqualTo(attendues);
    }

    @Test
    void unJetonSansRoleMetierRecoitUneListeVide() throws Exception {
        assertThat(permissions("default-roles-asce-lc", "offline_access")).isEmpty();
    }

    @Test
    void leCgeNeRecoitAucunePermissionDAdministration() throws Exception {
        assertThat(permissions("CGE")).doesNotContain("ROLE_MODIFIER", "AGENT_CREER", "AUDIT_JOURNAL_VOIR");
    }

    private List<String> permissions(String... roles) throws Exception {
        Jwt jeton = Jwt.withTokenValue("jeton-de-test").header("alg", "none").subject("utilisateur-de-test")
                .claim("realm_access", Map.of("roles", List.of(roles)))
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(300)).build();
        RequestPostProcessor auth = jwt().jwt(jeton).authorities(authorities.convert(jeton));
        String corps = mvc.perform(get(URL).with(auth)).andReturn().getResponse().getContentAsString();
        List<String> resultat = new ArrayList<>();
        JsonNode noeud = new ObjectMapper().readTree(corps).get("permissions");
        noeud.forEach(n -> resultat.add(n.asText()));
        return resultat;
    }
}
