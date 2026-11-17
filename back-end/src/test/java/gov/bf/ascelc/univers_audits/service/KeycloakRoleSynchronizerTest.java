package gov.bf.ascelc.univers_audits.service;

import gov.bf.ascelc.univers_audits.model.entity.RoleDefinition;
import gov.bf.ascelc.univers_audits.repository.RoleDefinitionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KeycloakRoleSynchronizerTest {

    @Mock private RoleDefinitionRepository roles;
    @Mock private KeycloakAdminService keycloak;

    private static RoleDefinition role(String cle, boolean actif) {
        return RoleDefinition.builder().roleKey(cle).label(cle).description("Description " + cle).active(actif).build();
    }

    private KeycloakRoleSynchronizer synchroniseur(boolean actif) {
        return new KeycloakRoleSynchronizer(roles, keycloak, actif);
    }

    @Test
    void creeDansKeycloakLesRolesActifsAbsentsDuRoyaume() {
        when(roles.findAll()).thenReturn(List.of(role("CGE", true), role("CHEF_DEI", true), role("ANCIEN", false)));
        when(keycloak.getAvailableRoles()).thenReturn(List.of("CGE"));

        synchroniseur(true).synchroniser();

        verify(keycloak).createRealmRole("CHEF_DEI", "Description CHEF_DEI");
        verify(keycloak, never()).createRealmRole(eq("CGE"), anyString());
        verify(keycloak, never()).createRealmRole(eq("ANCIEN"), anyString());
    }

    @Test
    void neSupprimeJamaisDeRoleKeycloak() {
        when(roles.findAll()).thenReturn(List.of(role("CGE", true)));
        when(keycloak.getAvailableRoles()).thenReturn(List.of("CGE", "ROLE_SANS_LIGNE_EN_BASE"));

        synchroniseur(true).synchroniser();

        verify(keycloak, never()).deleteRealmRole(anyString());
    }

    @Test
    void uneErreurSurUnRoleNArreteNiLesAutresNiLeDemarrage() {
        when(roles.findAll()).thenReturn(List.of(role("A_ROLE", true), role("B_ROLE", true)));
        when(keycloak.getAvailableRoles()).thenReturn(List.of());
        doThrow(new RuntimeException("Keycloak refuse")).when(keycloak).createRealmRole(eq("A_ROLE"), anyString());

        assertThatCode(() -> synchroniseur(true).synchroniser()).doesNotThrowAnyException();

        verify(keycloak).createRealmRole(eq("B_ROLE"), anyString());
    }

    @Test
    void unKeycloakInjoignableNEmpecheJamaisLeDemarrage() {
        when(keycloak.getAvailableRoles()).thenThrow(new RuntimeException("Keycloak injoignable"));

        assertThatCode(() -> synchroniseur(true).synchroniser()).doesNotThrowAnyException();

        verify(keycloak, never()).createRealmRole(anyString(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void neFaitRienQuandLaSynchronisationEstDesactivee() {
        synchroniseur(false).synchroniser();

        verifyNoInteractions(roles, keycloak);
    }

    @Test
    void uneDescriptionAbsenteNEstPasUnObstacle() {
        RoleDefinition sansDescription = RoleDefinition.builder().roleKey("CHEF_DAC").label("Chef du DAC").active(true).build();
        when(roles.findAll()).thenReturn(List.of(sansDescription));
        when(keycloak.getAvailableRoles()).thenReturn(List.of());

        synchroniseur(true).synchroniser();

        verify(keycloak).createRealmRole(eq("CHEF_DAC"), isNull());
    }
}
