package gov.bf.ascelc.univers_audits.controller;

import gov.bf.ascelc.univers_audits.model.dto.PermissionDto;
import gov.bf.ascelc.univers_audits.model.dto.RoleDto;
import gov.bf.ascelc.univers_audits.model.dto.request.CreateRoleRequest;
import gov.bf.ascelc.univers_audits.model.dto.request.UpdateRoleRequest;
import gov.bf.ascelc.univers_audits.service.AuditService;
import gov.bf.ascelc.univers_audits.service.RoleManagementService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/roles")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN_DDIC')")
public class RoleManagementController {

    private final RoleManagementService roleService;
    private final AuditService          auditService;


    private String id(Jwt jwt)   { return jwt != null ? jwt.getSubject()             : "SYSTEM"; }
    private String name(Jwt jwt) { return jwt != null ? jwt.getClaimAsString("name") : null; }


    @GetMapping
    public ResponseEntity<List<RoleDto>> listAll() {
        return ResponseEntity.ok(roleService.findAll());
    }

    @GetMapping("/{roleKey}")
    public ResponseEntity<RoleDto> getOne(@PathVariable String roleKey) {
        return ResponseEntity.ok(roleService.findByRoleKey(roleKey));
    }

    @GetMapping("/permissions")
    public ResponseEntity<List<PermissionDto>> listPermissions() {
        return ResponseEntity.ok(roleService.findAllPermissions());
    }


    @PostMapping
    public ResponseEntity<RoleDto> create(
            @Valid @RequestBody CreateRoleRequest req,
            HttpServletRequest httpRequest,
            @AuthenticationPrincipal Jwt jwt) {

        RoleDto result = roleService.create(req);

        auditService.logAction(
                id(jwt), name(jwt), "ADMIN_DDIC",
                "CREER_ROLE", "ROLE", result.roleKey(),
                "Création rôle : " + result.roleKey() + " — " + result.label(),
                httpRequest);

        return ResponseEntity.status(201).body(result);
    }

    @PutMapping("/{roleKey}")
    public ResponseEntity<RoleDto> update(
            @PathVariable String roleKey,
            @Valid @RequestBody UpdateRoleRequest req,
            HttpServletRequest httpRequest,
            @AuthenticationPrincipal Jwt jwt) {

        RoleDto result = roleService.update(roleKey, req);

        auditService.logAction(
                id(jwt), name(jwt), "ADMIN_DDIC",
                "MODIFIER_ROLE", "ROLE", roleKey,
                "Modification rôle : " + roleKey,
                httpRequest);

        return ResponseEntity.ok(result);
    }

    @DeleteMapping("/{roleKey}")
    public ResponseEntity<Void> delete(
            @PathVariable String roleKey,
            HttpServletRequest httpRequest,
            @AuthenticationPrincipal Jwt jwt) {

        roleService.delete(roleKey);

        auditService.logAction(
                id(jwt), name(jwt), "ADMIN_DDIC",
                "SUPPRIMER_ROLE", "ROLE", roleKey,
                "Suppression rôle : " + roleKey,
                httpRequest);

        return ResponseEntity.noContent().build();
    }
}