package gov.bf.ascelc.univers_audits.controller;

import gov.bf.ascelc.univers_audits.model.dto.request.MissionSuiviRequest;
import gov.bf.ascelc.univers_audits.model.dto.response.MissionSuiviListResponse;
import gov.bf.ascelc.univers_audits.service.MissionSuiviService;
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
@RequestMapping(ApiUrls.INVESTIGATIONS + "/{id}/missions-suivi")
public class MissionSuiviController {

    private static final String READ_ROLES =
            "hasAnyRole('CGEA','CGE','CONSEILLER_JURIDIQUE','CONTROLEUR_ETAT','MEMBRE_CTADP','ADMIN_DDIC')";
    private static final String WRITE_ROLES =
            "hasAnyRole('CONTROLEUR_ETAT','ADMIN_DDIC')";

    private final MissionSuiviService missionSuiviService;

    @GetMapping
    @PreAuthorize(READ_ROLES)
    public ResponseEntity<MissionSuiviListResponse> lister(@PathVariable UUID id) {
        return ResponseEntity.ok(missionSuiviService.lister(id));
    }

    @PostMapping
    @PreAuthorize(WRITE_ROLES)
    public ResponseEntity<MissionSuiviListResponse> ajouter(
            @PathVariable UUID id,
            @Valid @RequestBody MissionSuiviRequest request) {

        log.info("Enregistrement mission de suivi — investigation {}", id);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(missionSuiviService.ajouter(id, request));
    }
}
