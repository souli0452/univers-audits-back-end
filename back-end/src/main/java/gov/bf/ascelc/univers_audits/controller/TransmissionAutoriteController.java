package gov.bf.ascelc.univers_audits.controller;

import gov.bf.ascelc.univers_audits.model.dto.request.RelanceSuitesRequest;
import gov.bf.ascelc.univers_audits.model.dto.request.TransmissionAutoriteRequest;
import gov.bf.ascelc.univers_audits.model.dto.response.TransmissionAutoriteResponse;
import gov.bf.ascelc.univers_audits.security.permission.AppPermission;
import gov.bf.ascelc.univers_audits.security.permission.HasPermission;
import gov.bf.ascelc.univers_audits.service.TransmissionAutoriteService;
import gov.bf.ascelc.univers_audits.shared.exceptions.ResourceNotFoundException;
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
@RequestMapping(ApiUrls.INVESTIGATIONS + "/{id}/transmission-autorite")
public class TransmissionAutoriteController {

    private final TransmissionAutoriteService transmissionAutoriteService;

    @GetMapping
    @HasPermission(AppPermission.TRANSMISSION_AUTORITE_VOIR)
    public ResponseEntity<TransmissionAutoriteResponse> getTransmission(@PathVariable UUID id) {
        try {
            return ResponseEntity.ok(transmissionAutoriteService.getOrThrow(id));
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.noContent().build();
        }
    }

    @PostMapping
    @HasPermission(AppPermission.TRANSMISSION_AUTORITE_CREER)
    public ResponseEntity<TransmissionAutoriteResponse> creerTransmission(
            @PathVariable UUID id,
            @Valid @RequestBody TransmissionAutoriteRequest request) {

        log.info("Enregistrement transmission à l'autorité — investigation {}", id);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(transmissionAutoriteService.creer(id, request));
    }

    @PostMapping("/relances")
    @HasPermission(AppPermission.TRANSMISSION_AUTORITE_RELANCER)
    public ResponseEntity<TransmissionAutoriteResponse> ajouterRelance(
            @PathVariable UUID id,
            @Valid @RequestBody RelanceSuitesRequest request) {

        log.info("Ajout d'une relance — investigation {}", id);
        return ResponseEntity.ok(transmissionAutoriteService.ajouterRelance(id, request));
    }
}
