package gov.bf.ascelc.univers_audits.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/** Lit les vrais contrôles d'accès des contrôleurs (nécessite le contexte complet, comme contextLoads). */
@SpringBootTest
class AccessMatrixIntegrationTest {

    @Autowired
    private AccessMatrixService service;

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
}
