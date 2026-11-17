package gov.bf.ascelc.univers_audits.controller;

import gov.bf.ascelc.univers_audits.model.dto.request.PvConstatCreateRequest;
import gov.bf.ascelc.univers_audits.model.dto.request.VisiteTerrainConductRequest;
import gov.bf.ascelc.univers_audits.model.dto.request.VisiteTerrainScheduleRequest;
import gov.bf.ascelc.univers_audits.model.dto.response.PvConstatResponse;
import gov.bf.ascelc.univers_audits.model.dto.response.VisiteTerrainResponse;
import gov.bf.ascelc.univers_audits.security.permission.AppPermission;
import gov.bf.ascelc.univers_audits.security.permission.HasPermission;
import gov.bf.ascelc.univers_audits.service.PvConstatService;
import gov.bf.ascelc.univers_audits.service.VisiteTerrainService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/investigations/{investigationId}/visites-terrain")
@RequiredArgsConstructor
public class VisiteTerrainController {

    private final VisiteTerrainService visiteTerrainService;
    private final PvConstatService     pvConstatService;

    @GetMapping
    @HasPermission(AppPermission.VISITE_TERRAIN_VOIR)
    public ResponseEntity<List<VisiteTerrainResponse>> findAll(
            @PathVariable UUID investigationId) {
        return ResponseEntity.ok(visiteTerrainService.findByInvestigationId(investigationId));
    }

    @PostMapping
    @HasPermission(AppPermission.VISITE_TERRAIN_PLANIFIER)
    public ResponseEntity<VisiteTerrainResponse> schedule(
            @PathVariable UUID investigationId,
            @Valid @RequestBody VisiteTerrainScheduleRequest request) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(visiteTerrainService.schedule(investigationId, request));
    }

    @PatchMapping("/{visiteId}/conduct")
    @HasPermission(AppPermission.VISITE_TERRAIN_CONDUIRE)
    public ResponseEntity<VisiteTerrainResponse> conduct(
            @PathVariable UUID investigationId,
            @PathVariable UUID visiteId,
            @Valid @RequestBody VisiteTerrainConductRequest request) {
        return ResponseEntity.ok(visiteTerrainService.conduct(visiteId, request));
    }

    @PatchMapping("/{visiteId}/cancel")
    @HasPermission(AppPermission.VISITE_TERRAIN_PLANIFIER)
    public ResponseEntity<VisiteTerrainResponse> cancel(
            @PathVariable UUID investigationId,
            @PathVariable UUID visiteId,
            @RequestParam String reason) {
        return ResponseEntity.ok(visiteTerrainService.cancel(visiteId, reason));
    }

    @PatchMapping("/{visiteId}/carence")
    @HasPermission(AppPermission.VISITE_TERRAIN_PLANIFIER)
    public ResponseEntity<VisiteTerrainResponse> markCarence(
            @PathVariable UUID investigationId,
            @PathVariable UUID visiteId,
            @RequestParam String reason) {
        return ResponseEntity.ok(visiteTerrainService.markCarence(visiteId, reason));
    }

    @GetMapping("/{visiteId}/pv")
    @HasPermission(AppPermission.VISITE_TERRAIN_VOIR)
    public ResponseEntity<PvConstatResponse> getPv(
            @PathVariable UUID investigationId,
            @PathVariable UUID visiteId) {
        return ResponseEntity.ok(pvConstatService.findByVisiteId(visiteId));
    }

    @PostMapping("/{visiteId}/pv")
    @HasPermission(AppPermission.VISITE_TERRAIN_CONDUIRE)
    public ResponseEntity<PvConstatResponse> createPv(
            @PathVariable UUID investigationId,
            @PathVariable UUID visiteId,
            @Valid @RequestBody PvConstatCreateRequest request) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(pvConstatService.create(visiteId, request));
    }
}
