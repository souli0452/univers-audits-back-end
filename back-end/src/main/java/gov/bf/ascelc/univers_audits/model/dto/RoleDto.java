package gov.bf.ascelc.univers_audits.model.dto;

import java.util.List;

public record RoleDto(
        String  roleKey,
        String  label,
        String  description,
        String  icon,
        String  severity,
        int     displayOrder,
        boolean visible,
        boolean isProtected,
        List<PermissionDto> permissions
) {}