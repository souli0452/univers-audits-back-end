package gov.bf.ascelc.univers_audits.controller;

import gov.bf.ascelc.univers_audits.model.dto.request.ConstitutionPartieCivileRequest;
import gov.bf.ascelc.univers_audits.model.dto.response.ConstitutionPartieCivileResponse;
import gov.bf.ascelc.univers_audits.service.ConstitutionPartieCivileService;
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
@RequestMapping(ApiUrls.INVESTIGATIONS + "/{id}/constitution-partie-civile")
public class ConstitutionPartieCivileController {

    private static final String READ_ROLES =
            "hasAnyRole('CGEA','CGE','CONSEILLER_JURIDIQUE','CONTROLEUR_ETAT','MEMBRE_CTADP','ADMIN_DDIC')";
    private static final String WRITE_ROLES =
            "hasAnyRole('CGE','ADMIN_DDIC')";

    private final ConstitutionPartieCivileService constitutionPartieCivileService;

    @GetMapping
    @PreAuthorize(READ_ROLES)
    public ResponseEntity<ConstitutionPartieCivileResponse> getConstitution(@PathVariable UUID id) {
        try {
            return ResponseEntity.ok(constitutionPartieCivileService.getOrThrow(id));
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.noContent().build();
        }
    }

    @PostMapping
    @PreAuthorize(WRITE_ROLES)
    public ResponseEntity<ConstitutionPartieCivileResponse> creerConstitution(
            @PathVariable UUID id,
            @Valid @RequestBody ConstitutionPartieCivileRequest request) {

        log.info("Enregistrement constitution de partie civile — investigation {}", id);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(constitutionPartieCivileService.creer(id, request));
    }
}
