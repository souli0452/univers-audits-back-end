package gov.bf.ascelc.univers_audits.security.access;

import gov.bf.ascelc.univers_audits.security.access.EndpointAccess.Kind;
import gov.bf.ascelc.univers_audits.security.permission.AppPermission;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Garde la cohérence entre l'instantané des accès « avant », la table point d'entrée → permission, le catalogue
 * {@link AppPermission} et la migration Liquibase 028 qui sème les attributions de départ.
 * Régénérer la migration : {@code -Dmigration.generate=true}.
 */
class PermissionMigrationConsistencyTest {

    private static final Path MIGRATION = Path.of("src/main/resources/db/changelog/migrations/028-permissions-fines.sql");

    private final List<EndpointAccess> golden = AccessFixtures.golden();
    private final Map<String, AppPermission> table = AccessFixtures.endpointPermissions();

    @Test
    void chaqueLigneDeLaTableDesigneUnPointDentreeConnu() {
        Set<String> connus = AccessFixtures.cles(golden);
        assertThat(table.keySet()).as("points d'entrée de la table absents de l'instantané").isSubsetOf(connus);
    }

    @Test
    void chaquePointDentreeProtegeParRolesAUneEtUneSeulePermission() {
        List<String> sansPermission = new ArrayList<>();
        for (EndpointAccess a : golden) {
            if (a.kind() == Kind.ROLES && !table.containsKey(AccessFixtures.cle(a))) {
                sansPermission.add(a.handler() + " " + a.httpMethods());
            }
        }
        assertThat(sansPermission).as("points d'entrée protégés par rôles sans permission").isEmpty();
    }

    @Test
    void uneMemePermissionNeCouvreQueDesPointsDentreeAuxMemesRoles() {
        // Sinon le catalogue est trop grossier : le rôle gagnerait ou perdrait un accès à la migration.
        assertThat(PermissionMigrationSql.rolesParPermission(golden, table)).isNotEmpty();
    }

    @Test
    void laTableNeReferenceQueDesPointsDentreeProtegesParRoles() {
        for (EndpointAccess a : golden) {
            if (table.containsKey(AccessFixtures.cle(a))) {
                assertThat(a.kind()).as(a.handler()).isEqualTo(Kind.ROLES);
            }
        }
    }

    @Test
    void laMigration028EstExactementCelleQueLeGenerateurProduit() throws IOException {
        String attendu = PermissionMigrationSql.generer(golden, table);
        if (Boolean.getBoolean("migration.generate")) {
            Files.writeString(MIGRATION, attendu);
        }
        assertThat(Files.readString(MIGRATION)).as("migration 028 à jour (-Dmigration.generate=true)").isEqualTo(attendu);
    }

    @Test
    void lesCleDeLaMigrationSontExactementCellesDuCatalogue() throws IOException {
        String sql = Files.readString(MIGRATION);
        Set<String> inserees = new TreeSet<>(PermissionMigrationSql.clesInserees(sql));
        Set<String> catalogue = new TreeSet<>();
        for (AppPermission p : AppPermission.values()) {
            catalogue.add(p.key());
        }
        assertThat(inserees).isEqualTo(catalogue);
    }

    @Test
    void lesPermissionsProtegeesSontAttribueesAuRoleAdministrateur() {
        Map<AppPermission, SortedSet<String>> roles = PermissionMigrationSql.rolesParPermission(golden, table);
        for (AppPermission p : AppPermission.values()) {
            if (p.protegee()) {
                assertThat(roles.get(p)).as(p.key()).contains("ADMIN_DDIC");
            }
        }
    }

    @Test
    void lesAnciennesPermissionsIndicativesSontDesactiveesSansEtreSupprimees() throws IOException {
        String sql = Files.readString(MIGRATION);
        assertThat(sql).contains("UPDATE permission SET active = FALSE");
        assertThat(sql).doesNotContain("DELETE FROM permission");
        for (String ancienne : PermissionMigrationSql.ANCIENNES_PERMISSIONS) {
            assertThat(sql).contains("'" + ancienne + "'");
        }
    }
}
