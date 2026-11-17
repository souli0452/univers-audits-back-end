package gov.bf.ascelc.univers_audits.controller;

import gov.bf.ascelc.univers_audits.model.dto.request.MissionSuiviRequest;
import gov.bf.ascelc.univers_audits.model.dto.response.MissionSuiviListResponse;
import gov.bf.ascelc.univers_audits.security.permission.AppPermission;
import gov.bf.ascelc.univers_audits.security.permission.HasPermission;
import gov.bf.ascelc.univers_audits.service.MissionSuiviService;
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
@RequestMapping(ApiUrls.INVESTIGATIONS + "/{id}/missions-suivi")
public class MissionSuiviController {

    private final MissionSuiviService missionSuiviService;

    @GetMapping
    @HasPermission(AppPermission.MISSION_SUIVI_VOIR)
    public ResponseEntity<MissionSuiviListResponse> lister(@PathVariable UUID id) {
        return ResponseEntity.ok(missionSuiviService.lister(id));
    }

    @PostMapping
    @HasPermission(AppPermission.MISSION_SUIVI_AJOUTER)
    public ResponseEntity<MissionSuiviListResponse> ajouter(
            @PathVariable UUID id,
            @Valid @RequestBody MissionSuiviRequest request) {

        log.info("Enregistrement mission de suivi — investigation {}", id);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(missionSuiviService.ajouter(id, request));
    }
}
