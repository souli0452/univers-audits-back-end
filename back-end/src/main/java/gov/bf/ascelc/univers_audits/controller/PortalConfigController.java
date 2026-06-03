package gov.bf.ascelc.univers_audits.controller;

import gov.bf.ascelc.univers_audits.model.entity.PortalConfig;
import gov.bf.ascelc.univers_audits.service.PortalConfigService;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class PortalConfigController {

    private final PortalConfigService portalConfigService;


    @GetMapping("/api/v1/public/portal-config")
    public ResponseEntity<Map<String, String>> getPublicConfig() {
        return ResponseEntity.ok(portalConfigService.findAllAsMap());
    }

    @GetMapping("/api/v1/admin/portal-config")
    @PreAuthorize("hasRole('ADMIN_DDIC')")
    public ResponseEntity<Map<String, List<PortalConfig>>> getAdminConfig() {
        return ResponseEntity.ok(portalConfigService.findAllGrouped());
    }

    @PutMapping("/api/v1/admin/portal-config/{configKey}")
    @PreAuthorize("hasRole('ADMIN_DDIC')")
    public ResponseEntity<PortalConfig> updateOne(
            @PathVariable String configKey,
            @RequestBody Map<String, @NotBlank String> body,
            @AuthenticationPrincipal Jwt jwt) {

        String updatedBy = jwt != null ? jwt.getClaimAsString("name") : "ADMIN";
        return ResponseEntity.ok(
                portalConfigService.update(configKey, body.get("value"), updatedBy));
    }

    @PutMapping("/api/v1/admin/portal-config/batch")
    @PreAuthorize("hasRole('ADMIN_DDIC')")
    public ResponseEntity<List<PortalConfig>> updateBatch(
            @RequestBody Map<String, String> updates,
            @AuthenticationPrincipal Jwt jwt) {

        String updatedBy = jwt != null ? jwt.getClaimAsString("name") : "ADMIN";
        return ResponseEntity.ok(
                portalConfigService.updateBatch(updates, updatedBy));
    }
}
