package gov.bf.ascelc.univers_audits.controller;

import gov.bf.ascelc.univers_audits.model.dto.request.FicheRetexRequest;
import gov.bf.ascelc.univers_audits.model.dto.request.PublierLeconRequest;
import gov.bf.ascelc.univers_audits.model.dto.response.FicheRetexResponse;
import gov.bf.ascelc.univers_audits.model.dto.response.LeconAPartagerResponse;
import gov.bf.ascelc.univers_audits.security.permission.AppPermission;
import gov.bf.ascelc.univers_audits.security.permission.HasPermission;
import gov.bf.ascelc.univers_audits.service.FicheRetexService;
import gov.bf.ascelc.univers_audits.service.LeconAPartagerService;
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
@RequestMapping(ApiUrls.INVESTIGATIONS + "/{id}/fiche-retex")
public class FicheRetexController {

    private final FicheRetexService ficheRetexService;
    private final LeconAPartagerService leconAPartagerService;

    @PostMapping
    @HasPermission(AppPermission.FICHE_RETEX_REDIGER)
    public ResponseEntity<FicheRetexResponse> creer(
            @PathVariable UUID id,
            @Valid @RequestBody FicheRetexRequest request) {
        log.info("Rédaction fiche RETEX — investigation: {}", id);
        FicheRetexResponse result = ficheRetexService.creer(id, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    @GetMapping
    @HasPermission(AppPermission.FICHE_RETEX_VOIR)
    public ResponseEntity<FicheRetexResponse> obtenir(@PathVariable UUID id) {
        return ResponseEntity.ok(ficheRetexService.obtenir(id));
    }

    @PostMapping("/publier-lecon")
    @HasPermission(AppPermission.LECON_PUBLIER)
    public ResponseEntity<LeconAPartagerResponse> publierLecon(
            @PathVariable UUID id,
            @Valid @RequestBody PublierLeconRequest request) {
        log.info("Publication leçon à partager — investigation: {}", id);
        LeconAPartagerResponse result = leconAPartagerService.publier(id, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }
}
