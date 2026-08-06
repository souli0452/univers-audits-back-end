package gov.bf.ascelc.univers_audits.controller;

import gov.bf.ascelc.univers_audits.model.dto.request.PvConstatCreateRequest;
import gov.bf.ascelc.univers_audits.model.dto.request.VisiteTerrainConductRequest;
import gov.bf.ascelc.univers_audits.model.dto.request.VisiteTerrainScheduleRequest;
import gov.bf.ascelc.univers_audits.model.dto.response.PvConstatResponse;
import gov.bf.ascelc.univers_audits.model.dto.response.VisiteTerrainResponse;
import gov.bf.ascelc.univers_audits.service.PvConstatService;
import gov.bf.ascelc.univers_audits.service.VisiteTerrainService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/investigations/{investigationId}/visites-terrain")
@RequiredArgsConstructor
public class VisiteTerrainController {

    private static final String READ_ROLES =
            "hasAnyRole('CGEA','CGE','CONTROLEUR_ETAT','MEMBRE_CTADP','CONSEILLER_JURIDIQUE','ADMIN_DDIC')";
    private static final String WRITE_ROLES =
            "hasAnyRole('CONTROLEUR_ETAT','CGEA','ADMIN_DDIC')";

    private final VisiteTerrainService visiteTerrainService;
    private final PvConstatService     pvConstatService;

    @GetMapping
    @PreAuthorize(READ_ROLES)
    public ResponseEntity<List<VisiteTerrainResponse>> findAll(
            @PathVariable UUID investigationId) {
        return ResponseEntity.ok(visiteTerrainService.findByInvestigationId(investigationId));
    }

    @PostMapping
    @PreAuthorize(WRITE_ROLES)
    public ResponseEntity<VisiteTerrainResponse> schedule(
            @PathVariable UUID investigationId,
            @Valid @RequestBody VisiteTerrainScheduleRequest request) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(visiteTerrainService.schedule(investigationId, request));
    }

    @PatchMapping("/{visiteId}/conduct")
    @PreAuthorize(WRITE_ROLES)
    public ResponseEntity<VisiteTerrainResponse> conduct(
            @PathVariable UUID investigationId,
            @PathVariable UUID visiteId,
            @Valid @RequestBody VisiteTerrainConductRequest request) {
        return ResponseEntity.ok(visiteTerrainService.conduct(visiteId, request));
    }

    @PatchMapping("/{visiteId}/cancel")
    @PreAuthorize(WRITE_ROLES)
    public ResponseEntity<VisiteTerrainResponse> cancel(
            @PathVariable UUID investigationId,
            @PathVariable UUID visiteId,
            @RequestParam String reason) {
        return ResponseEntity.ok(visiteTerrainService.cancel(visiteId, reason));
    }

    @PatchMapping("/{visiteId}/carence")
    @PreAuthorize(WRITE_ROLES)
    public ResponseEntity<VisiteTerrainResponse> markCarence(
            @PathVariable UUID investigationId,
            @PathVariable UUID visiteId,
            @RequestParam String reason) {
        return ResponseEntity.ok(visiteTerrainService.markCarence(visiteId, reason));
    }

    @GetMapping("/{visiteId}/pv")
    @PreAuthorize(READ_ROLES)
    public ResponseEntity<PvConstatResponse> getPv(
            @PathVariable UUID investigationId,
            @PathVariable UUID visiteId) {
        return ResponseEntity.ok(pvConstatService.findByVisiteId(visiteId));
    }

    @PostMapping("/{visiteId}/pv")
    @PreAuthorize(WRITE_ROLES)
    public ResponseEntity<PvConstatResponse> createPv(
            @PathVariable UUID investigationId,
            @PathVariable UUID visiteId,
            @Valid @RequestBody PvConstatCreateRequest request) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(pvConstatService.create(visiteId, request));
    }
}
