package gov.bf.ascelc.univers_audits.controller;

import gov.bf.ascelc.univers_audits.model.dto.request.ChecklistCocheRequest;
import gov.bf.ascelc.univers_audits.model.dto.response.ChecklistDossierTravailItemResponse;
import gov.bf.ascelc.univers_audits.security.permission.AppPermission;
import gov.bf.ascelc.univers_audits.security.permission.HasPermission;
import gov.bf.ascelc.univers_audits.service.ChecklistDossierTravailService;
import gov.bf.ascelc.univers_audits.shared.utils.ApiUrls;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping(ApiUrls.INVESTIGATIONS + "/{id}/checklist")
public class ChecklistDossierTravailController {

    private final ChecklistDossierTravailService checklistDossierTravailService;

    @GetMapping
    @HasPermission(AppPermission.CHECKLIST_VOIR)
    public ResponseEntity<List<ChecklistDossierTravailItemResponse>> getChecklist(
            @PathVariable UUID id) {
        return ResponseEntity.ok(checklistDossierTravailService.getChecklist(id));
    }

    @PutMapping("/{pointCode}")
    @HasPermission(AppPermission.CHECKLIST_COCHER)
    public ResponseEntity<ChecklistDossierTravailItemResponse> setCoche(
            @PathVariable UUID id,
            @PathVariable String pointCode,
            @Valid @RequestBody ChecklistCocheRequest request) {

        log.info("Check-list — investigation {}, point {}", id, pointCode);
        return ResponseEntity.ok(
                checklistDossierTravailService.setCoche(id, pointCode, request));
    }
}
