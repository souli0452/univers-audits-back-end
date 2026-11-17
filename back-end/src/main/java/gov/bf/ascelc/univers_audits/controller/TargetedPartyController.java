package gov.bf.ascelc.univers_audits.controller;

import gov.bf.ascelc.univers_audits.model.dto.request.TargetedPartyRequest;
import gov.bf.ascelc.univers_audits.model.dto.response.TargetedPartyResponse;
import gov.bf.ascelc.univers_audits.security.permission.AppPermission;
import gov.bf.ascelc.univers_audits.security.permission.HasPermission;
import gov.bf.ascelc.univers_audits.service.TargetedPartyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/dossiers/{dossierId}/parties")
@RequiredArgsConstructor
public class TargetedPartyController {

    private final TargetedPartyService targetedPartyService;

    @HasPermission(AppPermission.PARTIE_VISEE_VOIR)
    @GetMapping
    public ResponseEntity<List<TargetedPartyResponse>> findAll(
            @PathVariable UUID dossierId) {
        return ResponseEntity.ok(
                targetedPartyService.findByDossierId(dossierId));
    }

    @PostMapping
    @HasPermission(AppPermission.PARTIE_VISEE_GERER)
    public ResponseEntity<TargetedPartyResponse> create(
            @PathVariable UUID dossierId,
            @Valid @RequestBody TargetedPartyRequest request) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(targetedPartyService.create(dossierId, request));
    }

    @PutMapping("/{partyId}")
    @HasPermission(AppPermission.PARTIE_VISEE_GERER)
    public ResponseEntity<TargetedPartyResponse> update(
            @PathVariable UUID dossierId,
            @PathVariable UUID partyId,
            @Valid @RequestBody TargetedPartyRequest request) {
        return ResponseEntity.ok(
                targetedPartyService.update(dossierId, partyId, request));
    }

    @DeleteMapping("/{partyId}")
    @HasPermission(AppPermission.PARTIE_VISEE_SUPPRIMER)
    public ResponseEntity<Void> delete(
            @PathVariable UUID dossierId,
            @PathVariable UUID partyId) {
        targetedPartyService.delete(dossierId, partyId);
        return ResponseEntity.noContent().build();
    }
}