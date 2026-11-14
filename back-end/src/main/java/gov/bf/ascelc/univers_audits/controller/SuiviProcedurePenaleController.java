package gov.bf.ascelc.univers_audits.controller;

import gov.bf.ascelc.univers_audits.model.dto.request.SuiviProcedurePenaleRequest;
import gov.bf.ascelc.univers_audits.model.dto.response.SuiviProcedurePenaleListResponse;
import gov.bf.ascelc.univers_audits.security.permission.AppPermission;
import gov.bf.ascelc.univers_audits.security.permission.HasPermission;
import gov.bf.ascelc.univers_audits.service.SuiviProcedurePenaleService;
import gov.bf.ascelc.univers_audits.shared.utils.ApiUrls;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping(ApiUrls.INVESTIGATIONS + "/{id}/suivi-procedure-penale")
public class SuiviProcedurePenaleController {

    private final SuiviProcedurePenaleService suiviProcedurePenaleService;

    @GetMapping
    @HasPermission(AppPermission.PROCEDURE_PENALE_VOIR)
    public ResponseEntity<SuiviProcedurePenaleListResponse> lister(@PathVariable UUID id) {
        return ResponseEntity.ok(suiviProcedurePenaleService.lister(id));
    }

    @PostMapping
    @HasPermission(AppPermission.PROCEDURE_PENALE_AJOUTER)
    public ResponseEntity<SuiviProcedurePenaleListResponse> ajouter(
            @PathVariable UUID id,
            @Valid @RequestBody SuiviProcedurePenaleRequest request) {

        log.info("Enregistrement suivi de procédure pénale — investigation {}", id);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(suiviProcedurePenaleService.ajouter(id, request));
    }
}
