package gov.bf.ascelc.univers_audits.controller;

import gov.bf.ascelc.univers_audits.model.dto.request.HabilitationGrantRequest;
import gov.bf.ascelc.univers_audits.model.dto.response.DossierHabilitationResponse;
import gov.bf.ascelc.univers_audits.security.permission.AppPermission;
import gov.bf.ascelc.univers_audits.security.permission.HasPermission;
import gov.bf.ascelc.univers_audits.service.DossierHabilitationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/dossiers/{dossierId}/habilitations")
@RequiredArgsConstructor
public class DossierHabilitationController {

    private final DossierHabilitationService habilitationService;

    @GetMapping
    @HasPermission(AppPermission.HABILITATION_VOIR)
    public ResponseEntity<List<DossierHabilitationResponse>> findActive(
            @PathVariable UUID dossierId) {
        return ResponseEntity.ok(habilitationService.findActiveByDossier(dossierId));
    }

    @PostMapping
    @HasPermission(AppPermission.HABILITATION_ACCORDER)
    public ResponseEntity<DossierHabilitationResponse> grant(
            @PathVariable UUID dossierId,
            @Valid @RequestBody HabilitationGrantRequest request) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(habilitationService.grantManual(dossierId, request));
    }

    @DeleteMapping("/{agentId}")
    @HasPermission(AppPermission.HABILITATION_RETIRER)
    public ResponseEntity<Void> revoke(
            @PathVariable UUID dossierId,
            @PathVariable UUID agentId,
            @RequestParam String reason) {
        habilitationService.revokeManual(dossierId, agentId, reason);
        return ResponseEntity.noContent().build();
    }
}
