package gov.bf.ascelc.univers_audits.controller;

import gov.bf.ascelc.univers_audits.model.dto.request.DemandeDocumentsAddressErrorRequest;
import gov.bf.ascelc.univers_audits.model.dto.request.DemandeDocumentsCreateRequest;
import gov.bf.ascelc.univers_audits.model.dto.response.DemandeDocumentsResponse;
import gov.bf.ascelc.univers_audits.security.permission.AppPermission;
import gov.bf.ascelc.univers_audits.security.permission.HasPermission;
import gov.bf.ascelc.univers_audits.service.DemandeDocumentsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/investigations/{investigationId}/demandes-documents")
@RequiredArgsConstructor
public class DemandeDocumentsController {

    private final DemandeDocumentsService demandeDocumentsService;

    @GetMapping
    @HasPermission(AppPermission.DEMANDE_DOCUMENTS_VOIR)
    public ResponseEntity<List<DemandeDocumentsResponse>> findAll(
            @PathVariable UUID investigationId) {
        return ResponseEntity.ok(demandeDocumentsService.findByInvestigationId(investigationId));
    }

    @PostMapping
    @HasPermission(AppPermission.DEMANDE_DOCUMENTS_GERER)
    public ResponseEntity<DemandeDocumentsResponse> create(
            @PathVariable UUID investigationId,
            @Valid @RequestBody DemandeDocumentsCreateRequest request) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(demandeDocumentsService.create(investigationId, request));
    }

    @PatchMapping("/{id}/mark-received")
    @HasPermission(AppPermission.DEMANDE_DOCUMENTS_GERER)
    public ResponseEntity<DemandeDocumentsResponse> markReceived(
            @PathVariable UUID investigationId,
            @PathVariable UUID id) {
        return ResponseEntity.ok(demandeDocumentsService.markReceived(id));
    }

    @PatchMapping("/{id}/escalate")
    @HasPermission(AppPermission.DEMANDE_DOCUMENTS_GERER)
    public ResponseEntity<DemandeDocumentsResponse> escalate(
            @PathVariable UUID investigationId,
            @PathVariable UUID id) {
        return ResponseEntity.ok(demandeDocumentsService.escalate(id));
    }

    @PatchMapping("/{id}/adresse-erronee")
    @HasPermission(AppPermission.DEMANDE_DOCUMENTS_GERER)
    public ResponseEntity<DemandeDocumentsResponse> reportAddressError(
            @PathVariable UUID investigationId,
            @PathVariable UUID id,
            @Valid @RequestBody DemandeDocumentsAddressErrorRequest request) {
        return ResponseEntity.ok(demandeDocumentsService.reportAddressError(id, request));
    }
}
