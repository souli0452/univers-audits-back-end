package gov.bf.ascelc.univers_audits.controller;

import gov.bf.ascelc.univers_audits.model.dto.request.NoteAvancementRequest;
import gov.bf.ascelc.univers_audits.model.dto.request.PlanActionsRequest;
import gov.bf.ascelc.univers_audits.model.dto.response.PlanActionsStatusResponse;
import gov.bf.ascelc.univers_audits.service.PlanActionsService;
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
@RequestMapping(ApiUrls.INVESTIGATIONS + "/{id}/plan-actions")
public class PlanActionsController {

    private static final String READ_ROLES =
            "hasAnyRole('CGEA','CGE','CONSEILLER_JURIDIQUE','CONTROLEUR_ETAT','MEMBRE_CTADP','ADMIN_DDIC')";
    private static final String WRITE_ROLES =
            "hasAnyRole('CGEA','ADMIN_DDIC')";

    private final PlanActionsService planActionsService;

    @GetMapping
    @PreAuthorize(READ_ROLES)
    public ResponseEntity<PlanActionsStatusResponse> getStatus(@PathVariable UUID id) {
        return ResponseEntity.ok(planActionsService.getStatus(id));
    }

    @PostMapping
    @PreAuthorize(WRITE_ROLES)
    public ResponseEntity<PlanActionsStatusResponse> creerPlanActions(
            @PathVariable UUID id,
            @Valid @RequestBody PlanActionsRequest request) {

        log.info("Enregistrement plan d'actions — investigation {}", id);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(planActionsService.creer(id, request));
    }

    @PostMapping("/avancements")
    @PreAuthorize(WRITE_ROLES)
    public ResponseEntity<PlanActionsStatusResponse> ajouterAvancement(
            @PathVariable UUID id,
            @Valid @RequestBody NoteAvancementRequest request) {

        log.info("Ajout d'une note d'avancement — investigation {}", id);
        return ResponseEntity.ok(planActionsService.ajouterAvancement(id, request));
    }
}
