package gov.bf.ascelc.univers_audits.controller;

import gov.bf.ascelc.univers_audits.model.dto.request.AuditionConductRequest;
import gov.bf.ascelc.univers_audits.model.dto.request.AuditionScheduleRequest;
import gov.bf.ascelc.univers_audits.model.dto.response.AuditionResponse;
import gov.bf.ascelc.univers_audits.service.AuditionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/investigations/{investigationId}/auditions")
@RequiredArgsConstructor
public class AuditionController {

    private static final String READ_ROLES =
            "hasAnyRole('CGEA','CGE','CONTROLEUR_ETAT','MEMBRE_CTADP','CONSEILLER_JURIDIQUE','ADMIN_DDIC')";
    private static final String WRITE_ROLES =
            "hasAnyRole('CONTROLEUR_ETAT','CGEA','ADMIN_DDIC')";

    private final AuditionService auditionService;

    @GetMapping
    @PreAuthorize(READ_ROLES)
    public ResponseEntity<List<AuditionResponse>> findAll(
            @PathVariable UUID investigationId) {
        return ResponseEntity.ok(auditionService.findByInvestigationId(investigationId));
    }

    @PostMapping
    @PreAuthorize(WRITE_ROLES)
    public ResponseEntity<AuditionResponse> schedule(
            @PathVariable UUID investigationId,
            @Valid @RequestBody AuditionScheduleRequest request) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(auditionService.schedule(investigationId, request));
    }

    @PatchMapping("/{auditionId}/conduct")
    @PreAuthorize(WRITE_ROLES)
    public ResponseEntity<AuditionResponse> conduct(
            @PathVariable UUID investigationId,
            @PathVariable UUID auditionId,
            @Valid @RequestBody AuditionConductRequest request) {
        return ResponseEntity.ok(auditionService.conduct(auditionId, request));
    }

    @PatchMapping("/{auditionId}/cancel")
    @PreAuthorize(WRITE_ROLES)
    public ResponseEntity<AuditionResponse> cancel(
            @PathVariable UUID investigationId,
            @PathVariable UUID auditionId,
            @RequestParam String reason) {
        return ResponseEntity.ok(auditionService.cancel(auditionId, reason));
    }
}
