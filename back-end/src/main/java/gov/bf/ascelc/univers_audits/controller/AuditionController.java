package gov.bf.ascelc.univers_audits.controller;

import gov.bf.ascelc.univers_audits.model.dto.request.AuditionConductRequest;
import gov.bf.ascelc.univers_audits.model.dto.request.AuditionScheduleRequest;
import gov.bf.ascelc.univers_audits.model.dto.request.PvAuditionCorrectionRequest;
import gov.bf.ascelc.univers_audits.model.dto.request.PvAuditionCreateRequest;
import gov.bf.ascelc.univers_audits.model.dto.request.PvAuditionFinalizeRequest;
import gov.bf.ascelc.univers_audits.model.dto.response.AuditionResponse;
import gov.bf.ascelc.univers_audits.model.dto.response.PvAuditionResponse;
import gov.bf.ascelc.univers_audits.security.permission.AppPermission;
import gov.bf.ascelc.univers_audits.security.permission.HasPermission;
import gov.bf.ascelc.univers_audits.service.AuditionService;
import gov.bf.ascelc.univers_audits.service.PvAuditionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/investigations/{investigationId}/auditions")
@RequiredArgsConstructor
public class AuditionController {

    private final AuditionService auditionService;
    private final PvAuditionService pvAuditionService;

    @GetMapping
    @HasPermission(AppPermission.AUDITION_VOIR)
    public ResponseEntity<List<AuditionResponse>> findAll(
            @PathVariable UUID investigationId) {
        return ResponseEntity.ok(auditionService.findByInvestigationId(investigationId));
    }

    @PostMapping
    @HasPermission(AppPermission.AUDITION_PLANIFIER)
    public ResponseEntity<AuditionResponse> schedule(
            @PathVariable UUID investigationId,
            @Valid @RequestBody AuditionScheduleRequest request) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(auditionService.schedule(investigationId, request));
    }

    @PatchMapping("/{auditionId}/conduct")
    @HasPermission(AppPermission.AUDITION_CONDUIRE)
    public ResponseEntity<AuditionResponse> conduct(
            @PathVariable UUID investigationId,
            @PathVariable UUID auditionId,
            @Valid @RequestBody AuditionConductRequest request) {
        return ResponseEntity.ok(auditionService.conduct(auditionId, request));
    }

    @PatchMapping("/{auditionId}/cancel")
    @HasPermission(AppPermission.AUDITION_PLANIFIER)
    public ResponseEntity<AuditionResponse> cancel(
            @PathVariable UUID investigationId,
            @PathVariable UUID auditionId,
            @RequestParam String reason) {
        return ResponseEntity.ok(auditionService.cancel(auditionId, reason));
    }

    @PatchMapping("/{auditionId}/no-show")
    @HasPermission(AppPermission.AUDITION_CONDUIRE)
    public ResponseEntity<AuditionResponse> markNoShow(
            @PathVariable UUID investigationId,
            @PathVariable UUID auditionId,
            @RequestParam(required = false) String note) {
        return ResponseEntity.ok(auditionService.markNoShow(auditionId, note));
    }

    @GetMapping("/{auditionId}/pv")
    @HasPermission(AppPermission.AUDITION_VOIR)
    public ResponseEntity<PvAuditionResponse> getPv(
            @PathVariable UUID investigationId,
            @PathVariable UUID auditionId) {
        return ResponseEntity.ok(pvAuditionService.findByAuditionId(auditionId));
    }

    @PostMapping("/{auditionId}/pv")
    @HasPermission(AppPermission.AUDITION_PV_REDIGER)
    public ResponseEntity<PvAuditionResponse> createPv(
            @PathVariable UUID investigationId,
            @PathVariable UUID auditionId,
            @Valid @RequestBody PvAuditionCreateRequest request) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(pvAuditionService.create(auditionId, request));
    }

    @PatchMapping("/{auditionId}/pv/finalize")
    @HasPermission(AppPermission.AUDITION_PV_REDIGER)
    public ResponseEntity<PvAuditionResponse> finalizePv(
            @PathVariable UUID investigationId,
            @PathVariable UUID auditionId,
            @Valid @RequestBody PvAuditionFinalizeRequest request) {
        return ResponseEntity.ok(pvAuditionService.finalizeSignatures(auditionId, request));
    }

    @PatchMapping("/{auditionId}/pv/relecture")
    @HasPermission(AppPermission.AUDITION_PV_REDIGER)
    public ResponseEntity<PvAuditionResponse> markPvReadBack(
            @PathVariable UUID investigationId,
            @PathVariable UUID auditionId) {
        return ResponseEntity.ok(pvAuditionService.markReadBack(auditionId));
    }

    @PatchMapping("/{auditionId}/pv/correction")
    @HasPermission(AppPermission.AUDITION_PV_REDIGER)
    public ResponseEntity<PvAuditionResponse> correctPv(
            @PathVariable UUID investigationId,
            @PathVariable UUID auditionId,
            @Valid @RequestBody PvAuditionCorrectionRequest request) {
        return ResponseEntity.ok(pvAuditionService.correct(auditionId, request));
    }
}
