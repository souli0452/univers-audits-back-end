package gov.bf.ascelc.univers_audits.security.access;

import gov.bf.ascelc.univers_audits.security.access.EndpointAccess.Kind;
import gov.bf.ascelc.univers_audits.security.permission.AppPermission;
import gov.bf.ascelc.univers_audits.security.permission.PermissionResolver;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

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

    @Autowired
    private ApplicationContext context;

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
    void aucun_point_dentree_ne_porte_deux_controles_ni_un_controle_par_nom_de_role() {
        // Le collecteur ne lit qu'un contrôle par point d'entrée : sans cette vérification, un @PreAuthorize par
        // rôle oublié à côté d'un @HasPermission passerait inaperçu alors que Spring évaluerait les deux.
        RequestMappingHandlerMapping mapping =
                context.getBean("requestMappingHandlerMapping", RequestMappingHandlerMapping.class);
        List<String> problemes = new ArrayList<>();
        for (HandlerMethod hm : mapping.getHandlerMethods().values()) {
            Class<?> type = hm.getBeanType();
            if (!type.getName().startsWith("gov.bf.ascelc")) {
                continue;
            }
            String nom = type.getSimpleName() + "#" + hm.getMethod().getName();
            Set<PreAuthorize> surMethode = AnnotatedElementUtils.findAllMergedAnnotations(hm.getMethod(), PreAuthorize.class);
            Set<PreAuthorize> surClasse = AnnotatedElementUtils.findAllMergedAnnotations(type, PreAuthorize.class);
            if (surMethode.size() > 1 || surClasse.size() > 1) {
                problemes.add(nom + " : plusieurs contrôles d'accès sur le même élément");
            }
            Stream.concat(surMethode.stream(), surClasse.stream())
                    .map(PreAuthorize::value)
                    .filter(v -> v.contains("hasRole(") || v.contains("hasAnyRole("))
                    .forEach(v -> problemes.add(nom + " : contrôle par nom de rôle " + v));
        }
        assertThat(problemes).isEmpty();
    }

    @Test
    void le_nombre_de_points_dentree_est_celui_de_avant() {
        assertThat(collector.collect()).hasSameSizeAs(avant);
    }
}
