package gov.bf.ascelc.univers_audits.controller;

import gov.bf.ascelc.univers_audits.model.dto.request.EtudeOpportuniteRequest;
import gov.bf.ascelc.univers_audits.model.dto.response.EtudeOpportuniteResponse;
import gov.bf.ascelc.univers_audits.service.EtudeOpportuniteService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/dossiers/{dossierId}/etude-opportunite")
@RequiredArgsConstructor
public class EtudeOpportuniteController {

    private final EtudeOpportuniteService etudeOpportuniteService;

    @GetMapping
    @PreAuthorize("hasAnyRole('AGENT_BRPD','CONSEILLER_JURIDIQUE'," +
            "'MEMBRE_CTADP','CGEA','CGE','CONTROLEUR_ETAT','ADMIN_DDIC')")
    public ResponseEntity<EtudeOpportuniteResponse> find(
            @PathVariable UUID dossierId) {
        return ResponseEntity.ok(
                etudeOpportuniteService.findByDossierId(dossierId));
    }

    @PutMapping
    @PreAuthorize("hasAnyRole('CONSEILLER_JURIDIQUE','ADMIN_DDIC')")
    public ResponseEntity<EtudeOpportuniteResponse> upsert(
            @PathVariable UUID dossierId,
            @Valid @RequestBody EtudeOpportuniteRequest request) {
        return ResponseEntity.ok(
                etudeOpportuniteService.upsert(dossierId, request));
    }
}
