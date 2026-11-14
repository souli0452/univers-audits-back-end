package gov.bf.ascelc.univers_audits.security.access;

import gov.bf.ascelc.univers_audits.security.access.EndpointAccess.Kind;
import gov.bf.ascelc.univers_audits.security.permission.AppPermission;
import gov.bf.ascelc.univers_audits.security.permission.PermissionResolver;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Preuve que la bascule des rôles vers les permissions ne change aucun accès : pour chaque point d'entrée et chaque
 * rôle, l'accès décidé aujourd'hui (permissions lues en base) est identique à celui d'avant la bascule
 * (instantané {@code access-golden-avant.json}, qui ne doit jamais être régénéré).
 */
@SpringBootTest
class AccessEquivalenceIntegrationTest {

    private static final List<String> ROLES = List.of(
            "ADMIN_DDIC", "CGE", "CGEA", "CONSEILLER_JURIDIQUE", "CONTROLEUR_ETAT", "MEMBRE_CTADP", "AGENT_BRPD", "DCP");

    @Autowired
    private EndpointAccessCollector collector;

    @Autowired
    private PermissionResolver resolver;

    private final List<EndpointAccess> avant = AccessFixtures.golden();
    private final Map<String, AppPermission> table = AccessFixtures.endpointPermissions();

    private Map<String, EndpointAccess> maintenant() {
        Map<String, EndpointAccess> parCle = new HashMap<>();
        collector.collect().forEach(a -> parCle.put(AccessFixtures.cle(a), a));
        return parCle;
    }

    @Test
    void chaque_role_garde_exactement_ses_acces() {
        Map<String, EndpointAccess> live = maintenant();
        List<String> ecarts = new ArrayList<>();
        for (EndpointAccess ancien : avant) {
            if (ancien.kind() != Kind.ROLES) {
                continue;
            }
            assertThat(ROLES).as("rôles de " + ancien.handler()).containsAll(ancien.roles());
            EndpointAccess actuel = live.get(AccessFixtures.cle(ancien));
            if (actuel == null || actuel.kind() != Kind.PERMISSION) {
                ecarts.add(ancien.handler() + " : pas converti en permission (" + (actuel == null ? "absent" : actuel.kind()) + ")");
                continue;
            }
            String autorite = AppPermission.AUTHORITY_PREFIX + actuel.permission().orElseThrow();
            for (String role : ROLES) {
                boolean avantBascule = ancien.roles().contains(role);
                boolean apresBascule = resolver.resolve(List.of(role)).contains(autorite);
                if (avantBascule != apresBascule) {
                    ecarts.add(ancien.handler() + " [" + role + "] : avant=" + avantBascule + " après=" + apresBascule);
                }
            }
        }
        assertThat(ecarts).as("écarts d'accès entre avant et après la bascule").isEmpty();
    }

    @Test
    void les_points_dentree_sans_role_sont_inchanges() {
        Map<String, EndpointAccess> live = maintenant();
        for (EndpointAccess ancien : avant) {
            if (ancien.kind() == Kind.ROLES) {
                continue;
            }
            EndpointAccess actuel = live.get(AccessFixtures.cle(ancien));
            assertThat(actuel).as(ancien.handler()).isNotNull();
            assertThat(actuel.kind()).as(ancien.handler()).isEqualTo(ancien.kind());
            assertThat(Objects.toString(actuel.expression())).as(ancien.handler()).isEqualTo(Objects.toString(ancien.expression()));
        }
    }

    @Test
    void la_permission_portee_par_le_code_est_celle_de_la_table() {
        Map<String, EndpointAccess> live = maintenant();
        table.forEach((cle, permission) -> {
            EndpointAccess actuel = live.get(cle);
            assertThat(actuel).as(cle).isNotNull();
            assertThat(actuel.permission()).as(cle).contains(permission.key());
        });
    }

    @Test
    void aucun_controle_par_nom_de_role_ne_reste_dans_les_controleurs() {
        List<String> restants = collector.collect().stream()
                .filter(a -> a.kind() == Kind.ROLES)
                .map(a -> a.handler() + " " + a.roles())
                .toList();
        assertThat(restants).isEmpty();
    }

    @Test
    void le_nombre_de_points_dentree_est_celui_de_avant() {
        assertThat(collector.collect()).hasSameSizeAs(avant);
    }
}
