package gov.bf.ascelc.univers_audits.controller;

import gov.bf.ascelc.univers_audits.model.dto.request.FicheRetexRequest;
import gov.bf.ascelc.univers_audits.model.dto.request.PublierLeconRequest;
import gov.bf.ascelc.univers_audits.model.dto.response.FicheRetexResponse;
import gov.bf.ascelc.univers_audits.model.dto.response.LeconAPartagerResponse;
import gov.bf.ascelc.univers_audits.service.FicheRetexService;
import gov.bf.ascelc.univers_audits.service.LeconAPartagerService;
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
@RequestMapping(ApiUrls.INVESTIGATIONS + "/{id}/fiche-retex")
public class FicheRetexController {

    private static final String READ_ROLES =
            "hasAnyRole('CGEA','CGE','CONSEILLER_JURIDIQUE','CONTROLEUR_ETAT','MEMBRE_CTADP','ADMIN_DDIC')";
    private static final String WRITE_ROLES =
            "hasAnyRole('CONTROLEUR_ETAT','ADMIN_DDIC')";

    private final FicheRetexService ficheRetexService;
    private final LeconAPartagerService leconAPartagerService;

    @PostMapping
    @PreAuthorize(WRITE_ROLES)
    public ResponseEntity<FicheRetexResponse> creer(
            @PathVariable UUID id,
            @Valid @RequestBody FicheRetexRequest request) {
        log.info("Rédaction fiche RETEX — investigation: {}", id);
        FicheRetexResponse result = ficheRetexService.creer(id, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    @GetMapping
    @PreAuthorize(READ_ROLES)
    public ResponseEntity<FicheRetexResponse> obtenir(@PathVariable UUID id) {
        return ResponseEntity.ok(ficheRetexService.obtenir(id));
    }

    @PostMapping("/publier-lecon")
    @PreAuthorize(WRITE_ROLES)
    public ResponseEntity<LeconAPartagerResponse> publierLecon(
            @PathVariable UUID id,
            @Valid @RequestBody PublierLeconRequest request) {
        log.info("Publication leçon à partager — investigation: {}", id);
        LeconAPartagerResponse result = leconAPartagerService.publier(id, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }
}
