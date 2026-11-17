package gov.bf.ascelc.univers_audits.controller;

import gov.bf.ascelc.univers_audits.model.dto.request.ObservationRequest;
import gov.bf.ascelc.univers_audits.model.dto.response.ObservationResponse;
import gov.bf.ascelc.univers_audits.security.permission.AppPermission;
import gov.bf.ascelc.univers_audits.security.permission.HasPermission;
import gov.bf.ascelc.univers_audits.service.ObservationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/dossiers/{dossierId}/observations")
@RequiredArgsConstructor
public class ObservationController {

    private final ObservationService observationService;

    @HasPermission(AppPermission.OBSERVATION_VOIR)
    @GetMapping
    public ResponseEntity<List<ObservationResponse>> findAll(
            @PathVariable UUID dossierId) {
        return ResponseEntity.ok(
                observationService.findByDossierId(dossierId));
    }

    @HasPermission(AppPermission.OBSERVATION_AJOUTER)
    @PostMapping
    public ResponseEntity<ObservationResponse> create(
            @PathVariable UUID dossierId,
            @Valid @RequestBody ObservationRequest request) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(observationService.create(dossierId, request));
    }
}