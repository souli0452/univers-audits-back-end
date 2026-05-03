package gov.bf.ascelc.univers_audits.controller;

import gov.bf.ascelc.univers_audits.model.dto.request.TargetedPartyRequest;
import gov.bf.ascelc.univers_audits.model.dto.response.TargetedPartyResponse;
import gov.bf.ascelc.univers_audits.service.TargetedPartyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/dossiers/{dossierId}/parties")
@RequiredArgsConstructor
public class TargetedPartyController {

    private final TargetedPartyService targetedPartyService;

    @GetMapping
    @PreAuthorize("hasAnyRole('AGENT_BRPD','CONSEILLER_JURIDIQUE'," +
            "'CONTROLEUR_ETAT','MEMBRE_CTADP','CGEA','CGE','ADMIN_DDIC')")
    public ResponseEntity<List<TargetedPartyResponse>> findAll(
            @PathVariable UUID dossierId) {
        return ResponseEntity.ok(
                targetedPartyService.findByDossierId(dossierId));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('AGENT_BRPD','CONTROLEUR_ETAT','ADMIN_DDIC')")
    public ResponseEntity<TargetedPartyResponse> create(
            @PathVariable UUID dossierId,
            @Valid @RequestBody TargetedPartyRequest request) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(targetedPartyService.create(dossierId, request));
    }

    @PutMapping("/{partyId}")
    @PreAuthorize("hasAnyRole('AGENT_BRPD','CONTROLEUR_ETAT','ADMIN_DDIC')")
    public ResponseEntity<TargetedPartyResponse> update(
            @PathVariable UUID dossierId,
            @PathVariable UUID partyId,
            @Valid @RequestBody TargetedPartyRequest request) {
        return ResponseEntity.ok(
                targetedPartyService.update(dossierId, partyId, request));
    }

    @DeleteMapping("/{partyId}")
    @PreAuthorize("hasAnyRole('AGENT_BRPD','CGEA','ADMIN_DDIC')")
    public ResponseEntity<Void> delete(
            @PathVariable UUID dossierId,
            @PathVariable UUID partyId) {
        targetedPartyService.delete(dossierId, partyId);
        return ResponseEntity.noContent().build();
    }
}