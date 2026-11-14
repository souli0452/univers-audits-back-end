package gov.bf.ascelc.univers_audits.security.access;

import gov.bf.ascelc.univers_audits.security.access.EndpointAccess.Kind;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;

class EndpointAccessTest {

    @Test
    void kindOf_uneListeDeRoles_estROLES() {
        assertThat(EndpointAccessCollector.kindOf("hasAnyRole('CGE','ADMIN_DDIC')")).isEqualTo(Kind.ROLES);
        assertThat(EndpointAccessCollector.kindOf("hasRole('ADMIN_DDIC')")).isEqualTo(Kind.ROLES);
    }

    @Test
    void kindOf_isAuthenticated_estAUTHENTICATED() {
        assertThat(EndpointAccessCollector.kindOf("isAuthenticated()")).isEqualTo(Kind.AUTHENTICATED);
    }

    @Test
    void kindOf_uneAutoriteDePermission_estPERMISSION() {
        assertThat(EndpointAccessCollector.kindOf("hasAuthority('PERM_DOSSIER_VOIR')")).isEqualTo(Kind.PERMISSION);
    }

    @Test
    void kindOf_uneRegleSansRoleNiPermission_estSPECIFIC() {
        assertThat(EndpointAccessCollector.kindOf("@accessGuard.canSee(#id)")).isEqualTo(Kind.SPECIFIC);
    }

    @Test
    void permission_extraiteDeLExpression() {
        EndpointAccess a = new EndpointAccess("DossierController#voir(UUID)", "GET", Kind.PERMISSION,
                new TreeSet<>(), "hasAuthority('PERM_DOSSIER_VOIR')");
        assertThat(a.permission()).contains("DOSSIER_VOIR");
        assertThat(new EndpointAccess("X#y()", "GET", Kind.NONE, new TreeSet<>(), null).permission()).isEmpty();
    }

    @Test
    void module_estLeNomDuControleurSansLeSuffixe() {
        EndpointAccess a = new EndpointAccess("SeanceCtadpController#liste()", "GET", Kind.ROLES,
                new TreeSet<>(Set.of("CGE")), "hasRole('CGE')");
        assertThat(a.module()).isEqualTo("SeanceCtadp");
    }

    @Test
    void lecture_vraiPourGETHEADOuToutesMethodes() {
        assertThat(access("GET").lecture()).isTrue();
        assertThat(access("HEAD,GET").lecture()).isTrue();
        assertThat(access("").lecture()).isTrue();
        assertThat(access("POST").lecture()).isFalse();
        assertThat(access("DELETE,PUT").lecture()).isFalse();
    }

    private static EndpointAccess access(String methodes) {
        return new EndpointAccess("DossierController#x()", methodes, Kind.ROLES,
                new TreeSet<>(Set.of("CGE")), "hasRole('CGE')");
    }
}
