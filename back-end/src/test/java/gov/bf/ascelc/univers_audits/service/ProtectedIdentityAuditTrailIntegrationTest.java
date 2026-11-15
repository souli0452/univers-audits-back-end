package gov.bf.ascelc.univers_audits.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import gov.bf.ascelc.univers_audits.security.permission.KeycloakAuthoritiesConverter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * Parcours réel, avec la base : une consultation d'identité protégée écrite par {@link CurrentUserAuditor} (écriture
 * asynchrone) est relue par l'API du Journal d'audit, que seul l'administrateur peut ouvrir.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ProtectedIdentityAuditTrailIntegrationTest {

    private static final String ACTION = "CONSULTER_IDENTITE_LANCEUR_ALERTE";

    @Autowired private CurrentUserAuditor auditor;
    @Autowired private MockMvc mvc;
    @Autowired private KeycloakAuthoritiesConverter authorities;
    @Autowired private JdbcTemplate jdbc;

    private final String sujet = "kc-test-" + UUID.randomUUID();
    private final String nom = "Testeur " + UUID.randomUUID();

    @AfterEach
    void nettoyer() {
        SecurityContextHolder.clearContext();
        jdbc.update("DELETE FROM audit_log WHERE agent_id = ?", sujet);
    }

    @Test
    void uneConsultationEcriteParLeCgeApparaitDansLeJournalDeLAdministrateur() throws Exception {
        connecter("CGE");

        auditor.record(ACTION, "DOSSIER", "d-1", "Consultation de l'identité du lanceur d'alerte protégé — dossier DOS-1");

        JsonNode entree = attendreLEntree();
        assertThat(entree.get("agentId").asText()).isEqualTo(sujet);
        assertThat(entree.get("agentName").asText()).isEqualTo(nom);
        assertThat(entree.get("agentRole").asText()).isEqualTo("CGE");
        assertThat(entree.get("action").asText()).isEqualTo(ACTION);
        assertThat(entree.get("entityType").asText()).isEqualTo("DOSSIER");
        assertThat(entree.get("description").asText()).contains("DOS-1");
        assertThat(entree.get("status").asText()).isEqualTo("SUCCESS");
    }

    @Test
    void leCgeNePeutPasLireLeJournalDAudit() throws Exception {
        int statut = mvc.perform(get("/api/v1/admin/audit").with(jeton("CGE"))).andReturn().getResponse().getStatus();

        assertThat(statut).isEqualTo(403);
    }

    private JsonNode attendreLEntree() throws Exception {
        long limite = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < limite) {
            String corps = mvc.perform(get("/api/v1/admin/audit")
                            .param("agentName", nom).param("action", ACTION).with(jeton("ADMIN_DDIC")))
                    .andReturn().getResponse().getContentAsString();
            JsonNode contenu = new ObjectMapper().readTree(corps).get("content");
            if (contenu != null && contenu.size() > 0) {
                return contenu.get(0);
            }
            Thread.sleep(200);
        }
        throw new AssertionError("L'entrée d'audit n'est pas apparue dans le journal en 10 s");
    }

    private void connecter(String... roles) {
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jetonBrut(sujet, roles)));
    }

    private RequestPostProcessor jeton(String... roles) {
        Jwt jeton = jetonBrut("lecteur-" + UUID.randomUUID(), roles);
        return jwt().jwt(jeton).authorities(authorities.convert(jeton));
    }

    private Jwt jetonBrut(String subject, String... roles) {
        return Jwt.withTokenValue("t").header("alg", "none").subject(subject).claim("name", nom)
                .claim("realm_access", Map.of("roles", List.of(roles)))
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(300)).build();
    }
}
