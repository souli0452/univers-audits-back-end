package gov.bf.ascelc.univers_audits.service;

import gov.bf.ascelc.univers_audits.security.permission.PermissionResolver;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

/** Lit les vrais contrôles d'accès des contrôleurs (nécessite le contexte complet, comme contextLoads). */
@SpringBootTest
class AccessMatrixIntegrationTest {

    @Autowired
    private AccessMatrixService service;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private PermissionResolver resolver;

    /** Le cache des droits est partagé par toute la suite : on ne laisse pas une modification de test dedans. */
    @AfterEach
    void viderLeCache() {
        resolver.invalidate();
    }

    @Test
    void matrice_refleteLesControlesDAccesDuCode() {
        var matrice = service.matrice();

        assertThat(matrice.roles()).extracting(AccessMatrixService.RoleInfo::code)
                .contains("ADMIN_DDIC", "CGE", "CGEA", "AGENT_BRPD");
        assertThat(matrice.modules()).isNotEmpty();

        // StatistiqueController : le tableau des dépassements par acteur est réservé à CGEA, CGE et ADMIN_DDIC
        var stats = matrice.modules().stream()
                .filter(m -> m.module().equals("Statistique")).findFirst().orElseThrow();
        assertThat(stats.parRole().get("CGEA").consulter()).isGreaterThan(stats.parRole().get("AGENT_BRPD").consulter());
        assertThat(stats.parRole().get("CGE").consulter()).isEqualTo(stats.parRole().get("CGEA").consulter());
    }

    @Test
    @Transactional
    void matrice_refleteUnePermissionRetireeEnBase() {
        int avant = consulterDepassements("CGE");

        jdbc.update("""
                DELETE FROM role_permission WHERE role_id = (SELECT id FROM role_definition WHERE role_key = 'CGE')
                  AND permission_id = (SELECT id FROM permission WHERE permission_key = 'STATISTIQUE_DEPASSEMENTS_VOIR')
                """);
        entityManager.clear(); // le SQL direct contourne le cache de session : on le vide pour relire la base
        resolver.invalidate();

        assertThat(consulterDepassements("CGE")).isLessThan(avant);
        assertThat(consulterDepassements("CGEA")).isGreaterThan(0);
    }

    private int consulterDepassements(String role) {
        return service.matrice().modules().stream()
                .filter(m -> m.module().equals("Statistique")).findFirst().orElseThrow()
                .parRole().get(role).consulter();
    }
}
