package gov.bf.ascelc.univers_audits.shared.config;

import org.junit.jupiter.api.Test;
import org.springframework.web.cors.CorsConfiguration;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Trouvé le 2026-09-28 lors du premier essai de dépôt réel en production : le domaine servi,
 * https://denoncer.asce-lc.bf, n'était pas dans la liste des origines autorisées — un navigateur reçoit
 * 403 sur toute requête publique (POST /api/v1/dossiers/public/submit) alors que curl sans en-tête
 * Origin obtient une réponse normale. La liste contenait deux domaines (portail./app.asce-lc.bf) qui ne
 * correspondent à aucun service réellement déployé (un seul domaine sert tout, cf. deploy-rpd).
 */
class SecurityConfigCorsTest {

    private CorsConfiguration cors() {
        return new SecurityConfig().corsConfigurationSource()
                .getCorsConfiguration(new org.springframework.mock.web.MockHttpServletRequest());
    }

    @Test
    void autoriseLeDomaineReellementServiEnProduction() {
        List<String> origines = cors().getAllowedOrigins();

        assertThat(origines).contains("https://denoncer.asce-lc.bf");
    }

    @Test
    void autoriseToujoursLeDeveloppementLocal() {
        assertThat(cors().getAllowedOrigins()).contains("http://localhost:4200");
    }

    /**
     * portail.asce-lc.bf et app.asce-lc.bf ne correspondent à aucun service réellement déployé
     * (un seul domaine sert tout, cf. deploy-rpd) : les garder autorisés en CORS avec
     * allowCredentials(true) est un risque dormant sans bénéfice, pas juste un oubli cosmétique.
     */
    @Test
    void ne_autorise_plus_les_domaines_obsoletes_sans_service_reel() {
        List<String> origines = cors().getAllowedOrigins();

        assertThat(origines)
                .doesNotContain("https://portail.asce-lc.bf", "https://app.asce-lc.bf");
    }

    /**
     * ged.asce-lc.bf est, au 2026-09-29, le seul domaine avec une adresse IP publique pointant vers
     * ce serveur (en attendant l'enregistrement DNS public de denoncer.asce-lc.bf) : contrairement à
     * portail./app.asce-lc.bf, il reste nécessaire.
     */
    @Test
    void autorise_toujours_le_domaine_public_actuel() {
        assertThat(cors().getAllowedOrigins()).contains("https://ged.asce-lc.bf");
    }

    @Test
    void ne_contient_aucun_doublon() {
        List<String> origines = cors().getAllowedOrigins();

        assertThat(origines).doesNotHaveDuplicates();
    }
}
