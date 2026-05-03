package gov.bf.ascelc.univers_audits.controller;

import gov.bf.ascelc.univers_audits.model.dto.request.WitnessRequest;
import gov.bf.ascelc.univers_audits.model.dto.response.WitnessResponse;
import gov.bf.ascelc.univers_audits.service.WitnessService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/dossiers/{dossierId}/witnesses")
@RequiredArgsConstructor
public class WitnessController {

    private final WitnessService witnessService;

    @GetMapping
    @PreAuthorize("hasAnyRole('AGENT_BRPD','CONSEILLER_JURIDIQUE'," +
            "'CONTROLEUR_ETAT','MEMBRE_CTADP','CGEA','CGE','ADMIN_DDIC')")
    public ResponseEntity<List<WitnessResponse>> findAll(
            @PathVariable UUID dossierId) {
        return ResponseEntity.ok(
                witnessService.findByDossierId(dossierId));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('AGENT_BRPD','CONTROLEUR_ETAT','ADMIN_DDIC')")
    public ResponseEntity<WitnessResponse> create(
            @PathVariable UUID dossierId,
            @Valid @RequestBody WitnessRequest request) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(witnessService.create(dossierId, request));
    }

    @PutMapping("/{witnessId}")
    @PreAuthorize("hasAnyRole('AGENT_BRPD','CONTROLEUR_ETAT','ADMIN_DDIC')")
    public ResponseEntity<WitnessResponse> update(
            @PathVariable UUID dossierId,
            @PathVariable UUID witnessId,
            @Valid @RequestBody WitnessRequest request) {
        return ResponseEntity.ok(
                witnessService.update(dossierId, witnessId, request));
    }

    @DeleteMapping("/{witnessId}")
    @PreAuthorize("hasAnyRole('AGENT_BRPD','CGEA','ADMIN_DDIC')")
    public ResponseEntity<Void> delete(
            @PathVariable UUID dossierId,
            @PathVariable UUID witnessId) {
        witnessService.delete(dossierId, witnessId);
        return ResponseEntity.noContent().build();
    }
}