package gov.bf.ascelc.univers_audits.controller;

import gov.bf.ascelc.univers_audits.model.dto.request.SuiviProcedurePenaleRequest;
import gov.bf.ascelc.univers_audits.model.dto.response.SuiviProcedurePenaleListResponse;
import gov.bf.ascelc.univers_audits.service.SuiviProcedurePenaleService;
import gov.bf.ascelc.univers_audits.shared.utils.ApiUrls;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping(ApiUrls.INVESTIGATIONS + "/{id}/suivi-procedure-penale")
public class SuiviProcedurePenaleController {

    private static final String READ_ROLES =
            "hasAnyRole('CGEA','CGE','CONSEILLER_JURIDIQUE','CONTROLEUR_ETAT','MEMBRE_CTADP','ADMIN_DDIC')";
    private static final String WRITE_ROLES =
            "hasAnyRole('CONSEILLER_JURIDIQUE','ADMIN_DDIC')";

    private final SuiviProcedurePenaleService suiviProcedurePenaleService;

    @GetMapping
    @PreAuthorize(READ_ROLES)
    public ResponseEntity<SuiviProcedurePenaleListResponse> lister(@PathVariable UUID id) {
        return ResponseEntity.ok(suiviProcedurePenaleService.lister(id));
    }

    @PostMapping
    @PreAuthorize(WRITE_ROLES)
    public ResponseEntity<SuiviProcedurePenaleListResponse> ajouter(
            @PathVariable UUID id,
            @Valid @RequestBody SuiviProcedurePenaleRequest request) {

        log.info("Enregistrement suivi de procédure pénale — investigation {}", id);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(suiviProcedurePenaleService.ajouter(id, request));
    }
}
