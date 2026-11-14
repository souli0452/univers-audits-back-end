package gov.bf.ascelc.univers_audits.security.permission;

import gov.bf.ascelc.univers_audits.model.entity.Permission;
import gov.bf.ascelc.univers_audits.model.entity.RoleDefinition;
import gov.bf.ascelc.univers_audits.repository.RoleDefinitionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PermissionResolverTest {

    private static final Duration TTL = Duration.ofMinutes(5);

    private final RoleDefinitionRepository repository = mock(RoleDefinitionRepository.class);
    private MutableClock clock;
    private PermissionResolver resolver;

    @BeforeEach
    void init() {
        clock = new MutableClock(Instant.parse("2026-11-14T10:00:00Z"));
        resolver = new PermissionResolver(repository, TTL, clock);
    }

    @Test
    void unRoleRecoitLesAutoritesDeSesPermissions() {
        when(repository.findActiveByRoleKeyInWithPermissions(anyCollection()))
                .thenReturn(List.of(role("CGE", "DOSSIER_VOIR", "DOSSIER_CLORE")));

        assertThat(resolver.resolve(List.of("CGE")))
                .containsExactlyInAnyOrder("PERM_DOSSIER_VOIR", "PERM_DOSSIER_CLORE");
    }

    @Test
    void plusieursRolesDonnentLUnionSansDoublon() {
        when(repository.findActiveByRoleKeyInWithPermissions(anyCollection())).thenReturn(List.of(
                role("CGE", "DOSSIER_VOIR", "DOSSIER_CLORE"),
                role("CGEA", "DOSSIER_VOIR", "SEANCE_CTADP_TENIR")));

        assertThat(resolver.resolve(List.of("CGE", "CGEA")))
                .containsExactlyInAnyOrder("PERM_DOSSIER_VOIR", "PERM_DOSSIER_CLORE", "PERM_SEANCE_CTADP_TENIR");
    }

    @Test
    void lesRolesTechniquesOuInconnusSontIgnoresSansErreur() {
        when(repository.findActiveByRoleKeyInWithPermissions(anyCollection())).thenReturn(List.of());

        assertThat(resolver.resolve(List.of("default-roles-asce-lc", "offline_access", "uma_authorization")))
                .isEmpty();
    }

    @Test
    void uneListeDeRolesVideOuNulleNeDonneRien() {
        assertThat(resolver.resolve(List.of())).isEmpty();
        assertThat(resolver.resolve(null)).isEmpty();
        verify(repository, times(0)).findActiveByRoleKeyInWithPermissions(anyCollection());
    }

    @Test
    void uneVieillePermissionInactiveEstIgnoree() {
        RoleDefinition cge = role("CGE", "DOSSIER_VOIR");
        Permission inactive = permission("DOSSIER_CLORE");
        inactive.setActive(false);
        cge.getPermissions().add(inactive);
        when(repository.findActiveByRoleKeyInWithPermissions(anyCollection())).thenReturn(List.of(cge));

        assertThat(resolver.resolve(List.of("CGE"))).containsExactly("PERM_DOSSIER_VOIR");
    }

    @Test
    void uneCleHorsCatalogueEstIgnoree() {
        when(repository.findActiveByRoleKeyInWithPermissions(anyCollection()))
                .thenReturn(List.of(role("CGE", "DOSSIER_VOIR", "VOIR_DOSSIER", "N_EXISTE_PLUS")));

        assertThat(resolver.resolve(List.of("CGE"))).containsExactly("PERM_DOSSIER_VOIR");
    }

    @Test
    void ledeuxiemeAppelEstServiPourLeCache() {
        when(repository.findActiveByRoleKeyInWithPermissions(anyCollection()))
                .thenReturn(List.of(role("CGE", "DOSSIER_VOIR")));

        resolver.resolve(List.of("CGE"));
        resolver.resolve(List.of("CGE"));

        verify(repository, times(1)).findActiveByRoleKeyInWithPermissions(anyCollection());
    }

    @Test
    void unRoleInconnuEstMisEnCacheAussiPourNePasInterrogerLaBaseASansCesse() {
        when(repository.findActiveByRoleKeyInWithPermissions(anyCollection())).thenReturn(List.of());

        resolver.resolve(List.of("offline_access"));
        resolver.resolve(List.of("offline_access"));

        verify(repository, times(1)).findActiveByRoleKeyInWithPermissions(anyCollection());
    }

    @Test
    void apresLeTtlLesPermissionsSontRelues() {
        when(repository.findActiveByRoleKeyInWithPermissions(anyCollection()))
                .thenReturn(List.of(role("CGE", "DOSSIER_VOIR")))
                .thenReturn(List.of(role("CGE", "DOSSIER_VOIR", "DOSSIER_CLORE")));

        assertThat(resolver.resolve(List.of("CGE"))).containsExactly("PERM_DOSSIER_VOIR");
        clock.avance(TTL.plusSeconds(1));
        assertThat(resolver.resolve(List.of("CGE"))).containsExactlyInAnyOrder("PERM_DOSSIER_VOIR", "PERM_DOSSIER_CLORE");
    }

    @Test
    void invalidateForceLaRelectureImmediate() {
        when(repository.findActiveByRoleKeyInWithPermissions(anyCollection()))
                .thenReturn(List.of(role("CGE", "DOSSIER_VOIR")))
                .thenReturn(List.of(role("CGE")));

        assertThat(resolver.resolve(List.of("CGE"))).containsExactly("PERM_DOSSIER_VOIR");
        resolver.invalidate();
        assertThat(resolver.resolve(List.of("CGE"))).isEmpty();
    }

    @Test
    void siLaBaseEstInjoignableAucunDroitNEstAccordeAUnRoleMetier() {
        when(repository.findActiveByRoleKeyInWithPermissions(anyCollection())).thenThrow(new IllegalStateException("base HS"));

        assertThat(resolver.resolve(List.of("CGE"))).isEmpty();
    }

    @Test
    void siLaBaseEstInjoignableLAdministrateurGardeSonSocle() {
        when(repository.findActiveByRoleKeyInWithPermissions(anyCollection())).thenThrow(new IllegalStateException("base HS"));

        Set<String> attendu = new HashSet<>();
        for (AppPermission p : AppPermission.values()) {
            if (p.protegee()) {
                attendu.add(p.authority());
            }
        }
        assertThat(resolver.resolve(List.of("ADMIN_DDIC"))).containsExactlyInAnyOrderElementsOf(attendu);
    }

    @Test
    void uneErreurDeLaBaseNEstPasMiseEnCache() {
        when(repository.findActiveByRoleKeyInWithPermissions(anyCollection()))
                .thenThrow(new IllegalStateException("base HS"))
                .thenReturn(List.of(role("CGE", "DOSSIER_VOIR")));

        assertThat(resolver.resolve(List.of("CGE"))).isEmpty();
        assertThat(resolver.resolve(List.of("CGE"))).containsExactly("PERM_DOSSIER_VOIR");
    }

    @Test
    void lAdministrateurGardeToujoursLesPermissionsProtegeesMemeSiLaBaseLesLuiRetire() {
        when(repository.findActiveByRoleKeyInWithPermissions(anyCollection()))
                .thenReturn(List.of(role("ADMIN_DDIC", "DOSSIER_VOIR")));

        assertThat(resolver.resolve(List.of("ADMIN_DDIC")))
                .contains("PERM_DOSSIER_VOIR", AppPermission.ROLE_MODIFIER.authority(),
                        AppPermission.AUDIT_JOURNAL_VOIR.authority());
    }

    @Test
    void lesAutresRolesNeRecoiventJamaisLeSocleAdministrateur() {
        when(repository.findActiveByRoleKeyInWithPermissions(anyCollection()))
                .thenReturn(List.of(role("CGE", "DOSSIER_VOIR")));

        assertThat(resolver.resolve(List.of("CGE"))).doesNotContain(AppPermission.ROLE_MODIFIER.authority());
    }

    private static RoleDefinition role(String key, String... permissions) {
        RoleDefinition role = RoleDefinition.builder().roleKey(key).label(key).build();
        for (String p : permissions) {
            role.getPermissions().add(permission(p));
        }
        return role;
    }

    private static Permission permission(String key) {
        return Permission.builder().permissionKey(key).label(key).build();
    }

    /** Horloge réglable pour tester l'expiration du cache. */
    private static final class MutableClock extends Clock {
        private Instant instant;

        MutableClock(Instant depart) {
            this.instant = depart;
        }

        void avance(Duration duree) {
            instant = instant.plus(duree);
        }

        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
