package gov.bf.ascelc.univers_audits.controller;

import gov.bf.ascelc.univers_audits.model.dto.request.ObservationRequest;
import gov.bf.ascelc.univers_audits.model.dto.response.ObservationResponse;
import gov.bf.ascelc.univers_audits.service.ObservationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/dossiers/{dossierId}/observations")
@RequiredArgsConstructor
public class ObservationController {

    private final ObservationService observationService;

    /**
     * Toutes les observations du dossier.
     * Les observations confidentielles ne sont visibles
     * que par CGE, CGEA et CONSEILLER_JURIDIQUE.
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('AGENT_BRPD','CONSEILLER_JURIDIQUE'," +
            "'CONTROLEUR_ETAT','MEMBRE_CTADP','CGEA','CGE','ADMIN_DDIC')")
    public ResponseEntity<List<ObservationResponse>> findAll(
            @PathVariable UUID dossierId) {
        return ResponseEntity.ok(
                observationService.findByDossierId(dossierId));
    }

    /**
     * Ajouter une observation (note interne, analyse, avis CTADP...).
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('AGENT_BRPD','CONSEILLER_JURIDIQUE'," +
            "'CONTROLEUR_ETAT','MEMBRE_CTADP','CGEA','CGE','ADMIN_DDIC')")
    public ResponseEntity<ObservationResponse> create(
            @PathVariable UUID dossierId,
            @Valid @RequestBody ObservationRequest request) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(observationService.create(dossierId, request));
    }
}