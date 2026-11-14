package gov.bf.ascelc.univers_audits.security.permission;

import lombok.RequiredArgsConstructor;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Autorités d'un jeton Keycloak : un {@code ROLE_xxx} par rôle du royaume (rôles techniques compris, pour ne rien
 * casser de l'existant) et un {@code PERM_xxx} par permission que la base accorde à ces rôles.
 */
@Component
@RequiredArgsConstructor
public class KeycloakAuthoritiesConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

    private final PermissionResolver resolver;

    @Override
    public Collection<GrantedAuthority> convert(Jwt jwt) {
        Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
        if (realmAccess == null || !(realmAccess.get("roles") instanceof Collection<?> brut)) {
            return List.of();
        }
        List<String> roles = new ArrayList<>();
        brut.forEach(r -> roles.add(String.valueOf(r)));

        List<GrantedAuthority> autorites = new ArrayList<>();
        roles.forEach(role -> autorites.add(new SimpleGrantedAuthority("ROLE_" + role)));
        resolver.resolve(roles).forEach(permission -> autorites.add(new SimpleGrantedAuthority(permission)));
        return autorites;
    }
}
