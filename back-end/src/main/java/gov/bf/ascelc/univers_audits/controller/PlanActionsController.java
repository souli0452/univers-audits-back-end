package gov.bf.ascelc.univers_audits.controller;

import gov.bf.ascelc.univers_audits.model.dto.request.NoteAvancementRequest;
import gov.bf.ascelc.univers_audits.model.dto.request.PlanActionsRequest;
import gov.bf.ascelc.univers_audits.model.dto.response.PlanActionsStatusResponse;
import gov.bf.ascelc.univers_audits.security.permission.AppPermission;
import gov.bf.ascelc.univers_audits.security.permission.HasPermission;
import gov.bf.ascelc.univers_audits.service.PlanActionsService;
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
@RequestMapping(ApiUrls.INVESTIGATIONS + "/{id}/plan-actions")
public class PlanActionsController {

    private final PlanActionsService planActionsService;

    @GetMapping
    @HasPermission(AppPermission.PLAN_ACTIONS_VOIR)
    public ResponseEntity<PlanActionsStatusResponse> getStatus(@PathVariable UUID id) {
        return ResponseEntity.ok(planActionsService.getStatus(id));
    }

    @PostMapping
    @HasPermission(AppPermission.PLAN_ACTIONS_GERER)
    public ResponseEntity<PlanActionsStatusResponse> creerPlanActions(
            @PathVariable UUID id,
            @Valid @RequestBody PlanActionsRequest request) {

        log.info("Enregistrement plan d'actions — investigation {}", id);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(planActionsService.creer(id, request));
    }

    @PostMapping("/avancements")
    @HasPermission(AppPermission.PLAN_ACTIONS_GERER)
    public ResponseEntity<PlanActionsStatusResponse> ajouterAvancement(
            @PathVariable UUID id,
            @Valid @RequestBody NoteAvancementRequest request) {

        log.info("Ajout d'une note d'avancement — investigation {}", id);
        return ResponseEntity.ok(planActionsService.ajouterAvancement(id, request));
    }
}
