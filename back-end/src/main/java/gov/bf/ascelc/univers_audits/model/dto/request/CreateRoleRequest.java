package gov.bf.ascelc.univers_audits.model.dto.request;

import jakarta.validation.constraints.*;
import java.util.List;

public record CreateRoleRequest(
        @NotBlank @Size(max = 60)
        @Pattern(regexp = "^[A-Z][A-Z0-9_]{1,59}$", message = "Majuscules et underscores uniquement")
        String roleKey,

        @NotBlank @Size(max = 150)
        String label,

        @Size(max = 500) String description,
        @Size(max = 60)  String icon,
        @Pattern(regexp = "^(success|info|warn|danger|secondary)$") String severity,
        Integer displayOrder,
        List<String> permissionKeys
) {}