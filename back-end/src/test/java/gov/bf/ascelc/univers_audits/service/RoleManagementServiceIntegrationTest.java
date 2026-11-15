package gov.bf.ascelc.univers_audits.service;

import gov.bf.ascelc.univers_audits.model.dto.PermissionDto;
import gov.bf.ascelc.univers_audits.model.dto.RoleDto;
import gov.bf.ascelc.univers_audits.security.permission.PermissionResolver;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/** Avec la vraie base : l'écran « Rôles & Permissions » montre exactement ce que chaque rôle reçoit. */
@SpringBootTest
class RoleManagementServiceIntegrationTest {

    @Autowired private RoleManagementService service;
    @Autowired private PermissionResolver resolver;

    @Test
    void lesPermissionsAfficheesSontCellesQueLeRoleRecoitReellement() {
        for (RoleDto role : service.findAll()) {
            Set<String> affichees = role.permissions().stream()
                    .map(PermissionDto::permissionKey).collect(Collectors.toSet());
            Set<String> recues = resolver.resolve(List.of(role.roleKey())).stream()
                    .map(a -> a.substring("PERM_".length())).collect(Collectors.toSet());

            assertThat(affichees).as(role.roleKey()).isEqualTo(recues);
        }
    }

    @Test
    void lesAnciennesPermissionsIndicativesNeSontPlusAffichees() {
        for (RoleDto role : service.findAll()) {
            assertThat(role.permissions()).as(role.roleKey())
                    .extracting(PermissionDto::permissionKey)
                    .doesNotContain("VOIR_DOSSIER", "CREER_DOSSIER", "GERER_ROLES", "VOIR_STATISTIQUES");
        }
    }
}
