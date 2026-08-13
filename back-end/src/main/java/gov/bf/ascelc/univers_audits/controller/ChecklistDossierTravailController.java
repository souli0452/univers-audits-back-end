package gov.bf.ascelc.univers_audits.controller;

import gov.bf.ascelc.univers_audits.model.dto.request.ChecklistCocheRequest;
import gov.bf.ascelc.univers_audits.model.dto.response.ChecklistDossierTravailItemResponse;
import gov.bf.ascelc.univers_audits.service.ChecklistDossierTravailService;
import gov.bf.ascelc.univers_audits.shared.utils.ApiUrls;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping(ApiUrls.INVESTIGATIONS + "/{id}/checklist")
public class ChecklistDossierTravailController {

    private static final String READ_ROLES =
            "hasAnyRole('CGEA','CGE','CONTROLEUR_ETAT','MEMBRE_CTADP','ADMIN_DDIC')";
    private static final String WRITE_ROLES =
            "hasAnyRole('CONTROLEUR_ETAT','ADMIN_DDIC')";

    private final ChecklistDossierTravailService checklistDossierTravailService;

    @GetMapping
    @PreAuthorize(READ_ROLES)
    public ResponseEntity<List<ChecklistDossierTravailItemResponse>> getChecklist(
            @PathVariable UUID id) {
        return ResponseEntity.ok(checklistDossierTravailService.getChecklist(id));
    }

    @PutMapping("/{pointCode}")
    @PreAuthorize(WRITE_ROLES)
    public ResponseEntity<ChecklistDossierTravailItemResponse> setCoche(
            @PathVariable UUID id,
            @PathVariable String pointCode,
            @Valid @RequestBody ChecklistCocheRequest request) {

        log.info("Check-list — investigation {}, point {}", id, pointCode);
        return ResponseEntity.ok(
                checklistDossierTravailService.setCoche(id, pointCode, request));
    }
}
