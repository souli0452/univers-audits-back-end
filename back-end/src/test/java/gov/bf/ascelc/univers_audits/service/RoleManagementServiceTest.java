package gov.bf.ascelc.univers_audits.service;

import gov.bf.ascelc.univers_audits.model.dto.request.CreateRoleRequest;
import gov.bf.ascelc.univers_audits.model.dto.request.UpdateRoleRequest;
import gov.bf.ascelc.univers_audits.model.entity.Permission;
import gov.bf.ascelc.univers_audits.model.entity.RoleDefinition;
import gov.bf.ascelc.univers_audits.repository.PermissionRepository;
import gov.bf.ascelc.univers_audits.repository.RoleDefinitionRepository;
import gov.bf.ascelc.univers_audits.security.permission.PermissionResolver;
import gov.bf.ascelc.univers_audits.shared.exceptions.BusinessException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RoleManagementServiceTest {

    @Mock private RoleDefinitionRepository roleRepo;
    @Mock private PermissionRepository permRepo;
    @Mock private PermissionResolver resolver;
    @Mock private KeycloakAdminService keycloak;
    @Mock private CurrentUserAuditor audit;

    private RoleManagementService service;

    @BeforeEach
    void setUp() {
        service = new RoleManagementService(roleRepo, permRepo, resolver, keycloak, audit);
        TransactionSynchronizationManager.initSynchronization();
    }

    @AfterEach
    void tearDown() {
        TransactionSynchronizationManager.clearSynchronization();
    }

    private static Permission perm(String key, boolean active) {
        return Permission.builder().permissionKey(key).label(key).category("Test").active(active).build();
    }

    private static Permission perm(String key) {
        return perm(key, true);
    }

    private static RoleDefinition role(String key, boolean protegee, Permission... perms) {
        return RoleDefinition.builder().roleKey(key).label(key).isProtected(protegee)
                .permissions(new HashSet<>(Set.of(perms))).build();
    }

    private void donnerLesPermissions(Permission... perms) {
        when(permRepo.findByPermissionKeyIn(any())).thenAnswer(inv -> {
            Set<String> demandees = inv.getArgument(0);
            return List.of(perms).stream().filter(p -> demandees.contains(p.getPermissionKey())).toList();
        });
    }

    private static void validerLaTransaction() {
        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
    }

    private static void annulerLaTransaction() {
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(s -> s.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
    }

    // ───────────────────────────── modification ─────────────────────────────

    @Test
    void update_refuseDeRetirerUnePermissionProtegeeALAdministrateur() {
        Permission voir = perm("ROLE_VOIR");
        Permission modifier = perm("ROLE_MODIFIER");
        Permission autre = perm("DOSSIER_VOIR");
        RoleDefinition admin = role("ADMIN_DDIC", true, voir, modifier, autre);
        when(roleRepo.findByRoleKeyWithPermissions("ADMIN_DDIC")).thenReturn(Optional.of(admin));
        donnerLesPermissions(voir, modifier, autre);

        assertThatThrownBy(() -> service.update("ADMIN_DDIC",
                new UpdateRoleRequest(null, null, null, null, null, null, List.of("ROLE_VOIR", "DOSSIER_VOIR"))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("ROLE_MODIFIER");

        verify(roleRepo, never()).save(any());
        verify(resolver, never()).invalidate();
        verifyNoInteractions(audit);
    }

    @Test
    void update_laRetraitDUnePermissionProtegeeNEstRefuseQuePourLAdministrateur() {
        Permission modifier = perm("ROLE_MODIFIER");
        RoleDefinition autreRole = role("CGE", false, modifier);
        when(roleRepo.findByRoleKeyWithPermissions("CGE")).thenReturn(Optional.of(autreRole));
        when(roleRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(roleRepo.findByRoleKeyWithPermissions("CGE")).thenReturn(Optional.of(autreRole));

        service.update("CGE", new UpdateRoleRequest(null, null, null, null, null, null, List.of()));

        assertThat(autreRole.getPermissions()).isEmpty();
    }

    @Test
    void update_refuseUnePermissionInconnue() {
        RoleDefinition cge = role("CGE", false);
        when(roleRepo.findByRoleKeyWithPermissions("CGE")).thenReturn(Optional.of(cge));
        donnerLesPermissions(perm("DOSSIER_VOIR"));

        assertThatThrownBy(() -> service.update("CGE",
                new UpdateRoleRequest(null, null, null, null, null, null, List.of("DOSSIER_VOIR", "N_IMPORTE_QUOI"))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("N_IMPORTE_QUOI");

        verify(roleRepo, never()).save(any());
    }

    @Test
    void update_refuseUnePermissionInactive() {
        RoleDefinition cge = role("CGE", false);
        when(roleRepo.findByRoleKeyWithPermissions("CGE")).thenReturn(Optional.of(cge));
        donnerLesPermissions(perm("VOIR_DOSSIER", false));

        assertThatThrownBy(() -> service.update("CGE",
                new UpdateRoleRequest(null, null, null, null, null, null, List.of("VOIR_DOSSIER"))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("VOIR_DOSSIER");

        verify(roleRepo, never()).save(any());
    }

    @Test
    void update_videLeCacheSeulementApresLeCommitEtJournaliseAvantEtApres() {
        Permission a = perm("DOSSIER_VOIR");
        Permission b = perm("DOSSIER_CREER");
        RoleDefinition cge = role("CGE", false, a);
        when(roleRepo.findByRoleKeyWithPermissions("CGE")).thenReturn(Optional.of(cge));
        when(roleRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        donnerLesPermissions(a, b);

        service.update("CGE", new UpdateRoleRequest(null, null, null, null, null, null,
                List.of("DOSSIER_VOIR", "DOSSIER_CREER")));

        verify(resolver, never()).invalidate();
        validerLaTransaction();
        verify(resolver).invalidate();

        ArgumentCaptor<String> description = ArgumentCaptor.forClass(String.class);
        verify(audit).record(eq("MODIFIER_PERMISSIONS_ROLE"), eq("ROLE"), eq("CGE"), description.capture());
        assertThat(description.getValue()).contains("CGE").contains("avant").contains("après")
                .contains("DOSSIER_VOIR").contains("DOSSIER_CREER");
    }

    @Test
    void update_nInvalideRienEtNeJournaliseRienSiLesPermissionsNeChangentPas() {
        Permission a = perm("DOSSIER_VOIR");
        RoleDefinition cge = role("CGE", false, a);
        when(roleRepo.findByRoleKeyWithPermissions("CGE")).thenReturn(Optional.of(cge));
        when(roleRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        donnerLesPermissions(a);

        service.update("CGE", new UpdateRoleRequest("Nouveau libellé", null, null, null, null, null, null));

        verifyNoInteractions(audit);
    }

    // ───────────────────────────── création ─────────────────────────────

    @Test
    void create_creeLeRoleDansKeycloakApresLEcritureEnBase() {
        when(roleRepo.existsByRoleKey("AUDITEUR")).thenReturn(false);
        when(roleRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        RoleDefinition cree = role("AUDITEUR", false);
        when(roleRepo.findByRoleKeyWithPermissions("AUDITEUR")).thenReturn(Optional.of(cree));

        service.create(new CreateRoleRequest("AUDITEUR", "Auditeur", "Décrit", null, null, null, null));

        InOrder ordre = inOrder(roleRepo, keycloak);
        ordre.verify(roleRepo).saveAndFlush(any());
        ordre.verify(keycloak).createRealmRole("AUDITEUR", "Décrit");
    }

    @Test
    void create_unEchecKeycloakFaitEchouerLaCreation_sansLaisserDeRoleOrphelin() {
        when(roleRepo.existsByRoleKey("AUDITEUR")).thenReturn(false);
        doThrow(new RuntimeException("Keycloak injoignable")).when(keycloak).createRealmRole(anyString(), any());

        assertThatThrownBy(() -> service.create(
                new CreateRoleRequest("AUDITEUR", "Auditeur", null, null, null, null, null)))
                .isInstanceOf(RuntimeException.class);

        // l'exception traverse la méthode transactionnelle : Spring annule l'écriture en base
        verify(keycloak, never()).deleteRealmRole(anyString());
        verify(resolver, never()).invalidate();
    }

    @Test
    void create_siLaTransactionEstAnnuleeApresLaCreationKeycloak_leRoleKeycloakEstSupprime() {
        when(roleRepo.existsByRoleKey("AUDITEUR")).thenReturn(false);
        when(roleRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(roleRepo.findByRoleKeyWithPermissions("AUDITEUR")).thenReturn(Optional.of(role("AUDITEUR", false)));

        service.create(new CreateRoleRequest("AUDITEUR", "Auditeur", null, null, null, null, null));
        annulerLaTransaction();

        verify(keycloak).deleteRealmRole("AUDITEUR");
        verify(resolver, never()).invalidate();
    }

    @Test
    void create_refuseUneCleDejaUtilisee() {
        when(roleRepo.existsByRoleKey("CGE")).thenReturn(true);

        assertThatThrownBy(() -> service.create(
                new CreateRoleRequest("CGE", "x", null, null, null, null, null)))
                .isInstanceOf(BusinessException.class);

        verifyNoInteractions(keycloak);
    }

    // ───────────────────────────── suppression ─────────────────────────────

    @Test
    void delete_refuseUnRoleProtege() {
        when(roleRepo.findByRoleKey("ADMIN_DDIC")).thenReturn(Optional.of(role("ADMIN_DDIC", true)));

        assertThatThrownBy(() -> service.delete("ADMIN_DDIC")).isInstanceOf(BusinessException.class);

        verify(roleRepo, never()).save(any());
        verifyNoInteractions(keycloak);
    }

    @Test
    void delete_refuseUnRoleEncorePorteParDesAgents() {
        when(roleRepo.findByRoleKey("AUDITEUR")).thenReturn(Optional.of(role("AUDITEUR", false)));
        when(keycloak.countRoleMembers("AUDITEUR")).thenReturn(3);

        assertThatThrownBy(() -> service.delete("AUDITEUR"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("3");

        verify(roleRepo, never()).save(any());
        verify(keycloak, never()).deleteRealmRole(anyString());
    }

    @Test
    void delete_refuseSiKeycloakNePermetPasDeVerifierLesMembres() {
        when(roleRepo.findByRoleKey("AUDITEUR")).thenReturn(Optional.of(role("AUDITEUR", false)));
        when(keycloak.countRoleMembers("AUDITEUR")).thenThrow(new RuntimeException("Keycloak injoignable"));

        assertThatThrownBy(() -> service.delete("AUDITEUR")).isInstanceOf(RuntimeException.class);

        verify(roleRepo, never()).save(any());
    }

    @Test
    void delete_desactiveLeRoleEnBasePuisSupprimeLeRoleKeycloakEtVideLeCacheApresLeCommit() {
        RoleDefinition auditeur = role("AUDITEUR", false);
        when(roleRepo.findByRoleKey("AUDITEUR")).thenReturn(Optional.of(auditeur));
        when(keycloak.countRoleMembers("AUDITEUR")).thenReturn(0);

        service.delete("AUDITEUR");

        assertThat(auditeur.getActive()).isFalse();
        verify(keycloak, never()).deleteRealmRole(anyString());
        verify(resolver, never()).invalidate();

        validerLaTransaction();

        verify(keycloak).deleteRealmRole("AUDITEUR");
        verify(resolver).invalidate();
    }

    @Test
    void delete_unEchecKeycloakApresLeCommitNeFaitPasEchouerLaRequete() {
        when(roleRepo.findByRoleKey("AUDITEUR")).thenReturn(Optional.of(role("AUDITEUR", false)));
        when(keycloak.countRoleMembers("AUDITEUR")).thenReturn(0);
        doThrow(new RuntimeException("Keycloak injoignable")).when(keycloak).deleteRealmRole("AUDITEUR");

        service.delete("AUDITEUR");
        validerLaTransaction();

        verify(resolver).invalidate();
    }
}
