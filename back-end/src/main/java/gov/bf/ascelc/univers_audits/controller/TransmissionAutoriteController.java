package gov.bf.ascelc.univers_audits.controller;

import gov.bf.ascelc.univers_audits.model.dto.request.RelanceSuitesRequest;
import gov.bf.ascelc.univers_audits.model.dto.request.TransmissionAutoriteRequest;
import gov.bf.ascelc.univers_audits.model.dto.response.TransmissionAutoriteResponse;
import gov.bf.ascelc.univers_audits.service.TransmissionAutoriteService;
import gov.bf.ascelc.univers_audits.shared.exceptions.ResourceNotFoundException;
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
@RequestMapping(ApiUrls.INVESTIGATIONS + "/{id}/transmission-autorite")
public class TransmissionAutoriteController {

    private static final String READ_ROLES =
            "hasAnyRole('CGEA','CGE','CONSEILLER_JURIDIQUE','CONTROLEUR_ETAT','MEMBRE_CTADP','ADMIN_DDIC')";
    private static final String WRITE_ROLES =
            "hasAnyRole('CGE','ADMIN_DDIC')";

    private final TransmissionAutoriteService transmissionAutoriteService;

    @GetMapping
    @PreAuthorize(READ_ROLES)
    public ResponseEntity<TransmissionAutoriteResponse> getTransmission(@PathVariable UUID id) {
        try {
            return ResponseEntity.ok(transmissionAutoriteService.getOrThrow(id));
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.noContent().build();
        }
    }

    @PostMapping
    @PreAuthorize(WRITE_ROLES)
    public ResponseEntity<TransmissionAutoriteResponse> creerTransmission(
            @PathVariable UUID id,
            @Valid @RequestBody TransmissionAutoriteRequest request) {

        log.info("Enregistrement transmission à l'autorité — investigation {}", id);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(transmissionAutoriteService.creer(id, request));
    }

    @PostMapping("/relances")
    @PreAuthorize(WRITE_ROLES)
    public ResponseEntity<TransmissionAutoriteResponse> ajouterRelance(
            @PathVariable UUID id,
            @Valid @RequestBody RelanceSuitesRequest request) {

        log.info("Ajout d'une relance — investigation {}", id);
        return ResponseEntity.ok(transmissionAutoriteService.ajouterRelance(id, request));
    }
}
