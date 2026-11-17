package gov.bf.ascelc.univers_audits.controller;

import gov.bf.ascelc.univers_audits.model.dto.request.InformationPreoccupanteCreateRequest;
import gov.bf.ascelc.univers_audits.model.dto.request.RattacherDossierRequest;
import gov.bf.ascelc.univers_audits.model.dto.response.DossierResponse;
import gov.bf.ascelc.univers_audits.model.dto.response.InformationPreoccupanteResponse;
import gov.bf.ascelc.univers_audits.security.permission.AppPermission;
import gov.bf.ascelc.univers_audits.security.permission.HasPermission;
import gov.bf.ascelc.univers_audits.service.InformationPreoccupanteService;
import gov.bf.ascelc.univers_audits.shared.utils.ApiUrls;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping(ApiUrls.INFORMATIONS_PREOCCUPANTES)
public class InformationPreoccupanteController {

    private final InformationPreoccupanteService informationPreoccupanteService;

    private String getClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        return (xff != null && !xff.isBlank())
                ? xff.split(",")[0].trim()
                : request.getRemoteAddr();
    }

    @PostMapping
    @HasPermission(AppPermission.INFO_PREOCCUPANTE_CREER)
    public ResponseEntity<InformationPreoccupanteResponse> create(
            @Valid @RequestBody InformationPreoccupanteCreateRequest request) {
        log.info("Création d'une information préoccupante — objet : {}", request.getObjet());
        InformationPreoccupanteResponse result = informationPreoccupanteService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    @GetMapping
    @HasPermission(AppPermission.INFO_PREOCCUPANTE_VOIR)
    public ResponseEntity<Page<InformationPreoccupanteResponse>> findAll(
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(informationPreoccupanteService.findAll(pageable));
    }

    @GetMapping("/{id}")
    @HasPermission(AppPermission.INFO_PREOCCUPANTE_VOIR)
    public ResponseEntity<InformationPreoccupanteResponse> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(informationPreoccupanteService.findById(id));
    }

    @PostMapping("/{id}/rattacher-dossier/{dossierId}")
    @HasPermission(AppPermission.INFO_PREOCCUPANTE_TRAITER)
    public ResponseEntity<InformationPreoccupanteResponse> rattacherDossier(
            @PathVariable UUID id,
            @PathVariable UUID dossierId,
            @Valid @RequestBody(required = false) RattacherDossierRequest request) {
        log.info("Rattachement dossier {} à l'information préoccupante {}", dossierId, id);
        return ResponseEntity.ok(
                informationPreoccupanteService.rattacherDossier(id, dossierId, request));
    }

    @PostMapping("/{id}/declencher-auto-saisine")
    @HasPermission(AppPermission.INFO_PREOCCUPANTE_AUTOSAISIR)
    public ResponseEntity<DossierResponse> declencherAutoSaisine(
            @PathVariable UUID id, HttpServletRequest httpRequest) {
        log.info("Déclenchement auto-saisine depuis l'information préoccupante {}", id);
        DossierResponse result = informationPreoccupanteService
                .declencherAutoSaisine(id, getClientIp(httpRequest));
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    @PostMapping("/{id}/classer-sans-suite")
    @HasPermission(AppPermission.INFO_PREOCCUPANTE_TRAITER)
    public ResponseEntity<InformationPreoccupanteResponse> classerSansSuite(@PathVariable UUID id) {
        log.info("Classement sans suite de l'information préoccupante {}", id);
        return ResponseEntity.ok(informationPreoccupanteService.classerSansSuite(id));
    }
}
