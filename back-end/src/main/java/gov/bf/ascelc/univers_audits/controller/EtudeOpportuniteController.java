package gov.bf.ascelc.univers_audits.controller;

import gov.bf.ascelc.univers_audits.model.dto.request.EtudeOpportuniteRequest;
import gov.bf.ascelc.univers_audits.model.dto.response.EtudeOpportuniteResponse;
import gov.bf.ascelc.univers_audits.security.permission.AppPermission;
import gov.bf.ascelc.univers_audits.security.permission.HasPermission;
import gov.bf.ascelc.univers_audits.service.EtudeOpportuniteService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/dossiers/{dossierId}/etude-opportunite")
@RequiredArgsConstructor
public class EtudeOpportuniteController {

    private final EtudeOpportuniteService etudeOpportuniteService;

    @HasPermission(AppPermission.ETUDE_OPPORTUNITE_VOIR)
    @GetMapping
    public ResponseEntity<EtudeOpportuniteResponse> find(
            @PathVariable UUID dossierId) {
        return ResponseEntity.ok(
                etudeOpportuniteService.findByDossierId(dossierId));
    }

    @PutMapping
    @HasPermission(AppPermission.ETUDE_OPPORTUNITE_REDIGER)
    public ResponseEntity<EtudeOpportuniteResponse> upsert(
            @PathVariable UUID dossierId,
            @Valid @RequestBody EtudeOpportuniteRequest request) {
        return ResponseEntity.ok(
                etudeOpportuniteService.upsert(dossierId, request));
    }
}
