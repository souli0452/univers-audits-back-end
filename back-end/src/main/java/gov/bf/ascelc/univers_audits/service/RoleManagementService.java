package gov.bf.ascelc.univers_audits.service;

import gov.bf.ascelc.univers_audits.model.dto.*;
import gov.bf.ascelc.univers_audits.model.dto.request.CreateRoleRequest;
import gov.bf.ascelc.univers_audits.model.dto.request.UpdateRoleRequest;
import gov.bf.ascelc.univers_audits.model.entity.*;
import gov.bf.ascelc.univers_audits.repository.*;
import gov.bf.ascelc.univers_audits.security.permission.AppPermission;
import gov.bf.ascelc.univers_audits.security.permission.PermissionResolver;
import gov.bf.ascelc.univers_audits.shared.exceptions.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RoleManagementService {

    private final RoleDefinitionRepository roleRepo;
    private final PermissionRepository     permRepo;
    private final PermissionResolver       resolver;
    private final KeycloakAdminService     keycloak;
    private final CurrentUserAuditor       auditor;

    @Transactional(readOnly = true)
    public List<RoleDto> findAll() {
        return roleRepo.findAllActiveWithPermissions()
                .stream()
                .map(r -> {
                    List<PermissionDto> perms = activesDe(r).stream()
                            .map(this::toPermDto)
                            .sorted(Comparator.comparing(PermissionDto::category)
                                    .thenComparing(PermissionDto::label))
                            .toList();
                    return toDto(r, perms);
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public RoleDto findByRoleKey(String roleKey) {
        RoleDefinition role = roleRepo.findByRoleKeyWithPermissions(roleKey)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Rôle introuvable : " + roleKey));

        List<PermissionDto> perms = activesDe(role).stream()
                .map(this::toPermDto)
                .sorted(Comparator.comparing(PermissionDto::category)
                        .thenComparing(PermissionDto::label))
                .toList();

        return toDto(role, perms);
    }

    @Transactional(readOnly = true)
    public List<PermissionDto> findAllPermissions() {
        return permRepo.findAllByActiveTrueOrderByCategoryAscLabelAsc()
                .stream()
                .map(this::toPermDto)
                .toList();
    }

    /**
     * Crée le rôle en base puis dans Keycloak. Si Keycloak refuse, l'exception annule l'écriture en base ; si la
     * transaction est annulée après la création dans Keycloak, le rôle Keycloak est supprimé : aucune moitié de rôle.
     */
    @Transactional
    public RoleDto create(CreateRoleRequest req) {
        if (roleRepo.existsByRoleKey(req.roleKey()))
            throw new BusinessException("Le rôle '" + req.roleKey() + "' existe déjà.");

        Set<Permission> permissions = req.permissionKeys() == null
                ? new HashSet<>() : resolveActivePermissions(req.permissionKeys());

        RoleDefinition role = RoleDefinition.builder()
                .roleKey(req.roleKey())
                .label(req.label())
                .description(req.description())
                .icon(req.icon()         != null ? req.icon()     : "pi pi-user")
                .severity(req.severity() != null ? req.severity() : "info")
                .displayOrder(req.displayOrder() != null ? req.displayOrder() : 99)
                .visible(true)
                .active(true)
                .isProtected(false)
                .permissions(permissions)
                .build();

        roleRepo.saveAndFlush(role);
        keycloak.createRealmRole(role.getRoleKey(), role.getDescription());
        apresAnnulation(() -> keycloak.deleteRealmRole(role.getRoleKey()));
        apresValidation(resolver::invalidate);

        log.info("[RoleManagement] Rôle créé : {}", role.getRoleKey());
        return findByRoleKey(role.getRoleKey());
    }

    @Transactional
    public RoleDto update(String roleKey, UpdateRoleRequest req) {
        RoleDefinition role = roleRepo.findByRoleKeyWithPermissions(roleKey)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Rôle introuvable : " + roleKey));

        Set<Permission> nouvelles = null;
        if (req.permissionKeys() != null) {
            nouvelles = resolveActivePermissions(req.permissionKeys());
            refuserLeRetraitDesPermissionsProtegees(role, nouvelles);
        }

        if (req.label()        != null) role.setLabel(req.label());
        if (req.description()  != null) role.setDescription(req.description());
        if (req.icon()         != null) role.setIcon(req.icon());
        if (req.severity()     != null) role.setSeverity(req.severity());
        if (req.displayOrder() != null) role.setDisplayOrder(req.displayOrder());
        if (req.visible()      != null) role.setVisible(req.visible());

        Set<String> avant = cles(activesDe(role));
        if (nouvelles != null) {
            role.setPermissions(nouvelles);
        }
        Set<String> apres = cles(activesDe(role));

        roleRepo.save(role);
        apresValidation(resolver::invalidate);
        if (!avant.equals(apres)) {
            journaliserLesPermissions(roleKey, avant, apres);
        }
        log.info("[RoleManagement] Rôle modifié : {}", roleKey);
        return findByRoleKey(roleKey);
    }

    /**
     * Désactive le rôle. Refusé s'il est protégé ou si des agents le portent encore (ils perdraient leurs accès en
     * silence) ; si Keycloak ne permet pas de le vérifier, on refuse aussi. Le rôle Keycloak est supprimé après le
     * commit : un échec à ce stade est journalisé sans annuler une désactivation déjà valide.
     */
    @Transactional
    public void delete(String roleKey) {
        RoleDefinition role = roleRepo.findByRoleKey(roleKey)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Rôle introuvable : " + roleKey));

        if (Boolean.TRUE.equals(role.getIsProtected()))
            throw new BusinessException(
                    "Le rôle '" + roleKey + "' est protégé et ne peut pas être supprimé.");

        int porteurs = keycloak.countRoleMembers(roleKey);
        if (porteurs > 0)
            throw new BusinessException("Le rôle '" + roleKey + "' est encore attribué à " + porteurs
                    + " agent(s) : retirez-le-leur avant de le supprimer.");

        role.setActive(false);
        role.setVisible(false);
        roleRepo.save(role);

        apresValidation(() -> {
            resolver.invalidate();
            try {
                keycloak.deleteRealmRole(roleKey);
            } catch (RuntimeException e) {
                log.error("[RoleManagement] Rôle '{}' désactivé en base mais pas supprimé dans Keycloak : {}",
                        roleKey, e.getMessage());
            }
        });
        log.warn("[RoleManagement] Rôle désactivé : {}", roleKey);
    }

    /** Permissions demandées, toutes présentes et actives ; sinon refus en les nommant. */
    private Set<Permission> resolveActivePermissions(Collection<String> keys) {
        Set<String> demandees = new LinkedHashSet<>(keys);
        if (demandees.isEmpty()) {
            return new HashSet<>();
        }
        List<Permission> trouvees = permRepo.findByPermissionKeyIn(demandees);
        Set<String> actives = trouvees.stream()
                .filter(p -> Boolean.TRUE.equals(p.getActive()))
                .map(Permission::getPermissionKey)
                .collect(Collectors.toSet());
        List<String> refusees = demandees.stream().filter(k -> !actives.contains(k)).toList();
        if (!refusees.isEmpty()) {
            throw new BusinessException("Permission inconnue ou inactive : " + String.join(", ", refusees));
        }
        return trouvees.stream().filter(p -> actives.contains(p.getPermissionKey()))
                .collect(Collectors.toCollection(HashSet::new));
    }

    /** L'administrateur technique doit toujours pouvoir gérer les rôles : on ne lui retire pas le socle. */
    private void refuserLeRetraitDesPermissionsProtegees(RoleDefinition role, Set<Permission> nouvelles) {
        if (!PermissionResolver.ROLE_ADMINISTRATEUR.equals(role.getRoleKey())) {
            return;
        }
        Set<String> gardees = cles(nouvelles);
        List<String> retirees = Arrays.stream(AppPermission.values())
                .filter(AppPermission::protegee)
                .map(AppPermission::key)
                .filter(k -> !gardees.contains(k))
                .toList();
        if (!retirees.isEmpty()) {
            throw new BusinessException("L'administrateur doit garder ces permissions, indispensables pour "
                    + "réparer les droits : " + String.join(", ", retirees));
        }
    }

    private void journaliserLesPermissions(String roleKey, Set<String> avant, Set<String> apres) {
        Set<String> ajoutees = new TreeSet<>(apres);
        ajoutees.removeAll(avant);
        Set<String> retirees = new TreeSet<>(avant);
        retirees.removeAll(apres);

        auditor.record("MODIFIER_PERMISSIONS_ROLE", "ROLE", roleKey,
                "Permissions du rôle " + roleKey + " — avant : " + new TreeSet<>(avant)
                        + " ; après : " + new TreeSet<>(apres)
                        + " ; ajoutées : " + ajoutees + " ; retirées : " + retirees);
    }

    /**
     * Permissions actives du rôle. Les anciennes permissions indicatives (migration 004), désactivées par la 028,
     * restent liées au rôle en base mais ne comptent plus : on ne les affiche ni ne les compare.
     */
    private static Set<Permission> activesDe(RoleDefinition role) {
        return role.getPermissions().stream()
                .filter(p -> Boolean.TRUE.equals(p.getActive()))
                .collect(Collectors.toSet());
    }

    private static Set<String> cles(Collection<Permission> permissions) {
        return permissions.stream().map(Permission::getPermissionKey).collect(Collectors.toSet());
    }

    /** Exécute l'action une fois la transaction validée (ou tout de suite hors transaction). */
    private static void apresValidation(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { action.run(); }
            });
        } else {
            action.run();
        }
    }

    /** Exécute l'action si la transaction est annulée : compense une écriture externe déjà faite. */
    private static void apresAnnulation(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCompletion(int status) {
                    if (status == STATUS_ROLLED_BACK) {
                        try {
                            action.run();
                        } catch (RuntimeException e) {
                            log.error("[RoleManagement] Compensation Keycloak échouée : {}", e.getMessage());
                        }
                    }
                }
            });
        }
    }

    private RoleDto toDto(RoleDefinition r, List<PermissionDto> perms) {
        return new RoleDto(
                r.getRoleKey(), r.getLabel(), r.getDescription(),
                r.getIcon(), r.getSeverity(), r.getDisplayOrder(),
                r.getVisible(), r.getIsProtected(), perms);
    }

    private PermissionDto toPermDto(Permission p) {
        return new PermissionDto(
                p.getPermissionKey(), p.getLabel(),
                p.getDescription(), p.getCategory());
    }
}
