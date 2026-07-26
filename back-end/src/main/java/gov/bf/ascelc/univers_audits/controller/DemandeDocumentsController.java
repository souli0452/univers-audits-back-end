package gov.bf.ascelc.univers_audits.controller;

import gov.bf.ascelc.univers_audits.model.dto.request.DemandeDocumentsCreateRequest;
import gov.bf.ascelc.univers_audits.model.dto.response.DemandeDocumentsResponse;
import gov.bf.ascelc.univers_audits.service.DemandeDocumentsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/investigations/{investigationId}/demandes-documents")
@RequiredArgsConstructor
public class DemandeDocumentsController {

    private static final String READ_ROLES =
            "hasAnyRole('CGEA','CGE','CONTROLEUR_ETAT','MEMBRE_CTADP','CONSEILLER_JURIDIQUE','ADMIN_DDIC')";
    private static final String WRITE_ROLES =
            "hasAnyRole('CONTROLEUR_ETAT','CGEA','ADMIN_DDIC')";

    private final DemandeDocumentsService demandeDocumentsService;

    @GetMapping
    @PreAuthorize(READ_ROLES)
    public ResponseEntity<List<DemandeDocumentsResponse>> findAll(
            @PathVariable UUID investigationId) {
        return ResponseEntity.ok(demandeDocumentsService.findByInvestigationId(investigationId));
    }

    @PostMapping
    @PreAuthorize(WRITE_ROLES)
    public ResponseEntity<DemandeDocumentsResponse> create(
            @PathVariable UUID investigationId,
            @Valid @RequestBody DemandeDocumentsCreateRequest request) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(demandeDocumentsService.create(investigationId, request));
    }

    @PatchMapping("/{id}/mark-received")
    @PreAuthorize(WRITE_ROLES)
    public ResponseEntity<DemandeDocumentsResponse> markReceived(
            @PathVariable UUID investigationId,
            @PathVariable UUID id) {
        return ResponseEntity.ok(demandeDocumentsService.markReceived(id));
    }

    @PatchMapping("/{id}/escalate")
    @PreAuthorize(WRITE_ROLES)
    public ResponseEntity<DemandeDocumentsResponse> escalate(
            @PathVariable UUID investigationId,
            @PathVariable UUID id) {
        return ResponseEntity.ok(demandeDocumentsService.escalate(id));
    }
}
