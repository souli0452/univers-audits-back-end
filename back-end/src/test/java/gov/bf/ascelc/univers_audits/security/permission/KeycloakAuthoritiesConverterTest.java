package gov.bf.ascelc.univers_audits.security.permission;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class KeycloakAuthoritiesConverterTest {

    private final PermissionResolver resolver = mock(PermissionResolver.class);
    private final KeycloakAuthoritiesConverter converter = new KeycloakAuthoritiesConverter(resolver);

    @Test
    void lesRolesDuJetonDeviennentDesAutoritesROLEEtLesPermissionsDesAutoritesPERM() {
        when(resolver.resolve(anyCollection())).thenReturn(Set.of("PERM_DOSSIER_VOIR"));

        Collection<GrantedAuthority> autorites =
                converter.convert(jeton(Map.of("roles", List.of("CGE", "offline_access"))));

        assertThat(autorites).extracting(GrantedAuthority::getAuthority)
                .containsExactlyInAnyOrder("ROLE_CGE", "ROLE_offline_access", "PERM_DOSSIER_VOIR");
    }

    @Test
    void lesPermissionsSontResoluesAPartirDesRolesDuJeton() {
        when(resolver.resolve(List.of("CGE", "DCP"))).thenReturn(Set.of("PERM_STATISTIQUE_GLOBALE_VOIR"));

        assertThat(converter.convert(jeton(Map.of("roles", List.of("CGE", "DCP")))))
                .extracting(GrantedAuthority::getAuthority).contains("PERM_STATISTIQUE_GLOBALE_VOIR");
    }

    @Test
    void sansRealmAccessAucuneAutoriteEtLaBaseNEstPasInterrogee() {
        assertThat(converter.convert(jeton(null))).isEmpty();
        verifyNoInteractions(resolver);
    }

    @Test
    void sansListeDeRolesAucuneAutorite() {
        assertThat(converter.convert(jeton(Map.of("autre", "x")))).isEmpty();
        verifyNoInteractions(resolver);
    }

    private static Jwt jeton(Map<String, Object> realmAccess) {
        Jwt.Builder b = Jwt.withTokenValue("t").header("alg", "none").subject("u")
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60));
        if (realmAccess != null) {
            b.claim("realm_access", realmAccess);
        }
        return b.build();
    }
}
