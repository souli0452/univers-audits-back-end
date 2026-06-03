package gov.bf.ascelc.univers_audits.model.dto;

public record PermissionDto(
        String permissionKey,
        String label,
        String description,
        String category
) {}