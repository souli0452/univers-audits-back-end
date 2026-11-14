package gov.bf.ascelc.univers_audits.security.permission;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * Appels HTTP réels sur les vrais contrôleurs, avec un jeton qui ne porte que des rôles Keycloak : les autorités
 * sont construites comme en production (rôles du jeton → permissions lues en base). Vérifie que Spring évalue bien
 * {@code @HasPermission}, dans les deux sens (accès accordé, accès refusé).
 */
@SpringBootTest
@AutoConfigureMockMvc
class PermissionEndToEndIntegrationTest {

    private static final String JOURNAL_AUDIT = "/api/v1/admin/audit";
    // Les deux paramètres de période sont obligatoires : sans eux Spring répond 400 avant le contrôle d'accès.
    private static final String STATISTIQUES =
            "/api/v1/stats/dashboard?start=2026-01-01T00:00:00Z&end=2026-12-31T23:59:59Z";
    private static final String DOSSIERS = "/api/v1/dossiers";
    private static final String AGENTS = "/api/v1/agents?page=0&size=1";
    private static final String ANNUAIRE = "/api/v1/agents/active";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private KeycloakAuthoritiesConverter authorities;

    @Test
    void sansJetonLaRequeteEstRefuseeAvecUne401() throws Exception {
        assertThat(statut(get(DOSSIERS))).isEqualTo(401);
    }

    @Test
    void lAdministrateurLitLeJournalDAudit() throws Exception {
        assertThat(statut(get(JOURNAL_AUDIT).with(jetonAvecRoles("ADMIN_DDIC")))).isNotIn(401, 403);
    }

    @Test
    void leCgeNeLitPasLeJournalDAudit() throws Exception {
        assertThat(statut(get(JOURNAL_AUDIT).with(jetonAvecRoles("CGE")))).isEqualTo(403);
    }

    @Test
    void laDcpVoitLesStatistiquesMaisPasLesDossiers() throws Exception {
        assertThat(statut(get(STATISTIQUES).with(jetonAvecRoles("DCP")))).isNotIn(401, 403);
        assertThat(statut(get(DOSSIERS).with(jetonAvecRoles("DCP")))).isEqualTo(403);
    }

    @Test
    void lAgentBrpdVoitLesDossiersMaisPasLesStatistiques() throws Exception {
        assertThat(statut(get(DOSSIERS).with(jetonAvecRoles("AGENT_BRPD")))).isNotIn(401, 403);
        assertThat(statut(get(STATISTIQUES).with(jetonAvecRoles("AGENT_BRPD")))).isEqualTo(403);
    }

    @Test
    void uneAutreSourceDeDroitsQueLesPermissionsNOuvreRien() throws Exception {
        // Un jeton qui ne porte que des rôles techniques n'a aucune permission.
        RequestPostProcessor technique = jetonAvecRoles("default-roles-asce-lc", "offline_access");
        assertThat(statut(get(DOSSIERS).with(technique))).isEqualTo(403);
        assertThat(statut(get(STATISTIQUES).with(technique))).isEqualTo(403);
        assertThat(statut(get(JOURNAL_AUDIT).with(technique))).isEqualTo(403);
    }

    @Test
    void lesRolesCumulesDonnentLUnionDesPermissions() throws Exception {
        RequestPostProcessor brpdEtDcp = jetonAvecRoles("AGENT_BRPD", "DCP");
        assertThat(statut(get(DOSSIERS).with(brpdEtDcp))).isNotIn(401, 403);
        assertThat(statut(get(STATISTIQUES).with(brpdEtDcp))).isNotIn(401, 403);
    }

    @Test
    void leCgeNeGerePasLesAgentsMaisLAdministrateurSi() throws Exception {
        assertThat(statut(get(AGENTS).with(jetonAvecRoles("CGE")))).isEqualTo(403);
        assertThat(statut(get(AGENTS).with(jetonAvecRoles("CGEA")))).isEqualTo(403);
        assertThat(statut(get(AGENTS).with(jetonAvecRoles("ADMIN_DDIC")))).isNotIn(401, 403);
    }

    @Test
    void lAnnuaireDesAgentsActifsEstOuvertAuMetierMaisPasALaDcp() throws Exception {
        assertThat(statut(get(ANNUAIRE).with(jetonAvecRoles("CGE")))).isNotIn(401, 403);
        assertThat(statut(get(ANNUAIRE).with(jetonAvecRoles("AGENT_BRPD")))).isNotIn(401, 403);
        assertThat(statut(get(ANNUAIRE).with(jetonAvecRoles("DCP")))).isEqualTo(403);
    }

    private int statut(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder requete) throws Exception {
        return mvc.perform(requete).andReturn().getResponse().getStatus();
    }

    private RequestPostProcessor jetonAvecRoles(String... roles) {
        Jwt jeton = Jwt.withTokenValue("jeton-de-test").header("alg", "none").subject("utilisateur-de-test")
                .claim("realm_access", Map.of("roles", List.of(roles)))
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(300)).build();
        return jwt().jwt(jeton).authorities(authorities.convert(jeton));
    }
}
