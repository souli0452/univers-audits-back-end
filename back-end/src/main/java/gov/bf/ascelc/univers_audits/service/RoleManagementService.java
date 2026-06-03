package gov.bf.ascelc.univers_audits.service;

import gov.bf.ascelc.univers_audits.model.dto.*;
import gov.bf.ascelc.univers_audits.model.dto.request.CreateRoleRequest;
import gov.bf.ascelc.univers_audits.model.dto.request.UpdateRoleRequest;
import gov.bf.ascelc.univers_audits.model.entity.*;
import gov.bf.ascelc.univers_audits.repository.*;
import gov.bf.ascelc.univers_audits.shared.exceptions.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class RoleManagementService {

    private final RoleDefinitionRepository roleRepo;
    private final PermissionRepository     permRepo;

    @Transactional(readOnly = true)
    public List<RoleDto> findAll() {
        return roleRepo.findAllActiveWithPermissions()
                .stream()
                .map(r -> {
                    List<PermissionDto> perms = r.getPermissions().stream()
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

        List<PermissionDto> perms = role.getPermissions().stream()
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

    @Transactional
    public RoleDto create(CreateRoleRequest req) {
        if (roleRepo.existsByRoleKey(req.roleKey()))
            throw new BusinessException("Le rôle '" + req.roleKey() + "' existe déjà.");

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
                .build();

        if (req.permissionKeys() != null && !req.permissionKeys().isEmpty()) {
            role.setPermissions(new HashSet<>(
                    permRepo.findByPermissionKeyIn(new HashSet<>(req.permissionKeys()))));
        }

        roleRepo.save(role);
        log.info("[RoleManagement] Rôle créé : {}", role.getRoleKey());
        return findByRoleKey(role.getRoleKey());
    }

    @Transactional
    public RoleDto update(String roleKey, UpdateRoleRequest req) {
        RoleDefinition role = roleRepo.findByRoleKeyWithPermissions(roleKey)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Rôle introuvable : " + roleKey));

        if (req.label()        != null) role.setLabel(req.label());
        if (req.description()  != null) role.setDescription(req.description());
        if (req.icon()         != null) role.setIcon(req.icon());
        if (req.severity()     != null) role.setSeverity(req.severity());
        if (req.displayOrder() != null) role.setDisplayOrder(req.displayOrder());
        if (req.visible()      != null) role.setVisible(req.visible());

        if (req.permissionKeys() != null) {
            role.setPermissions(req.permissionKeys().isEmpty()
                    ? new HashSet<>()
                    : new HashSet<>(permRepo.findByPermissionKeyIn(
                    new HashSet<>(req.permissionKeys()))));
        }

        roleRepo.save(role);
        log.info("[RoleManagement] Rôle modifié : {}", roleKey);
        return findByRoleKey(roleKey);
    }

    @Transactional
    public void delete(String roleKey) {
        RoleDefinition role = roleRepo.findByRoleKey(roleKey)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Rôle introuvable : " + roleKey));

        if (Boolean.TRUE.equals(role.getIsProtected()))
            throw new BusinessException(
                    "Le rôle '" + roleKey + "' est protégé et ne peut pas être supprimé.");

        role.setActive(false);
        role.setVisible(false);
        roleRepo.save(role);
        log.warn("[RoleManagement] Rôle désactivé : {}", roleKey);
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