package gov.bf.ascelc.univers_audits.security.permission;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class AppPermissionTest {

    @Test
    void chaqueCleRespecteLeFormatMODULE_ACTION() {
        for (AppPermission p : AppPermission.values()) {
            assertThat(p.key()).as(p.name()).matches("^[A-Z]+(_[A-Z]+)+$").hasSizeLessThanOrEqualTo(80);
        }
    }

    @Test
    void chaquePermissionAUneCategorieEtUnLibelle() {
        for (AppPermission p : AppPermission.values()) {
            assertThat(p.category()).as(p.name() + " catégorie").isNotBlank();
            assertThat(p.label()).as(p.name() + " libellé").isNotBlank();
        }
    }

    @Test
    void leLibelleDUneCategorieEstStable() {
        Set<String> categories = Arrays.stream(AppPermission.values())
                .map(AppPermission::category).collect(Collectors.toSet());
        assertThat(categories).containsExactlyInAnyOrder(
                "Dossiers", "Réception", "Étude d'opportunité et CTADP", "Investigations",
                "Auditions et visites", "Suites", "Documents", "Statistiques", "Notifications",
                "Paramétrage", "Administration", "Audit");
    }

    @Test
    void lAutoriteSpringEstLaCleprefixeePERM_() {
        assertThat(AppPermission.DOSSIER_VOIR.authority()).isEqualTo("PERM_DOSSIER_VOIR");
        assertThat(AppPermission.AUTHORITY_PREFIX).isEqualTo("PERM_");
    }

    @Test
    void ofRetrouveUnePermissionParSaCle() {
        assertThat(AppPermission.of("DOSSIER_VOIR")).contains(AppPermission.DOSSIER_VOIR);
        assertThat(AppPermission.of("N_EXISTE_PAS")).isEmpty();
        assertThat(AppPermission.of(null)).isEmpty();
    }

    @Test
    void lesPermissionsProtegeesPermettentDAdministrerLesRolesEtDeLireLeJournal() {
        List<AppPermission> protegees = Arrays.stream(AppPermission.values())
                .filter(AppPermission::protegee).toList();
        assertThat(protegees).containsExactlyInAnyOrder(
                AppPermission.ROLE_VOIR, AppPermission.ROLE_MODIFIER, AppPermission.AGENT_ROLES_ATTRIBUER,
                AppPermission.AUDIT_JOURNAL_VOIR, AppPermission.ACCES_MATRICE_VOIR);
    }
}
