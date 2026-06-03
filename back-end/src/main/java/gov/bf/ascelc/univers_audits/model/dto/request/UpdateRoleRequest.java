package gov.bf.ascelc.univers_audits.model.dto.request;

import jakarta.validation.constraints.*;
import java.util.List;

public record UpdateRoleRequest(
        @Size(max = 150) String label,
        @Size(max = 500) String description,
        @Size(max = 60)  String icon,
        @Pattern(regexp = "^(success|info|warn|danger|secondary)$") String severity,
        Integer displayOrder,
        Boolean visible,
        List<String> permissionKeys
) {}