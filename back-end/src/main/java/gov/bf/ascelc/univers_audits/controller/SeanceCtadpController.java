package gov.bf.ascelc.univers_audits.controller;

import gov.bf.ascelc.univers_audits.model.dto.request.AddDossierToSeanceRequest;
import gov.bf.ascelc.univers_audits.model.dto.request.RecommandationCtadpRequest;
import gov.bf.ascelc.univers_audits.model.dto.request.SeanceCtadpCreateRequest;
import gov.bf.ascelc.univers_audits.model.dto.request.TenirSeanceRequest;
import gov.bf.ascelc.univers_audits.model.dto.response.SeanceCtadpResponse;
import gov.bf.ascelc.univers_audits.service.SeanceCtadpService;
import gov.bf.ascelc.univers_audits.shared.utils.ApiUrls;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping(ApiUrls.SEANCES_CTADP)
@RequiredArgsConstructor
public class SeanceCtadpController {

    private final SeanceCtadpService seanceCtadpService;

    @PostMapping
    @PreAuthorize("hasAnyRole('CGEA','ADMIN_DDIC')")
    public ResponseEntity<SeanceCtadpResponse> create(
            @Valid @RequestBody SeanceCtadpCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(
                seanceCtadpService.create(request));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('AGENT_BRPD','CONSEILLER_JURIDIQUE'," +
            "'MEMBRE_CTADP','CGEA','CGE','CONTROLEUR_ETAT','ADMIN_DDIC')")
    public ResponseEntity<Page<SeanceCtadpResponse>> findAll(
            @PageableDefault(size = 20, sort = "dateSeance") Pageable pageable) {
        return ResponseEntity.ok(seanceCtadpService.findAll(pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('AGENT_BRPD','CONSEILLER_JURIDIQUE'," +
            "'MEMBRE_CTADP','CGEA','CGE','CONTROLEUR_ETAT','ADMIN_DDIC')")
    public ResponseEntity<SeanceCtadpResponse> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(seanceCtadpService.findById(id));
    }

    @PostMapping("/{id}/dossiers")
    @PreAuthorize("hasAnyRole('CGEA','ADMIN_DDIC')")
    public ResponseEntity<SeanceCtadpResponse> addDossier(
            @PathVariable UUID id,
            @Valid @RequestBody AddDossierToSeanceRequest request) {
        return ResponseEntity.ok(seanceCtadpService.addDossier(id, request));
    }

    @PutMapping("/{id}/dossiers/{dossierId}")
    @PreAuthorize("hasAnyRole('CGEA','CONSEILLER_JURIDIQUE','ADMIN_DDIC')")
    public ResponseEntity<SeanceCtadpResponse> recordRecommandation(
            @PathVariable UUID id,
            @PathVariable UUID dossierId,
            @Valid @RequestBody RecommandationCtadpRequest request) {
        return ResponseEntity.ok(
                seanceCtadpService.recordRecommandation(id, dossierId, request));
    }

    @PatchMapping("/{id}/tenir")
    @PreAuthorize("hasAnyRole('CGEA','ADMIN_DDIC')")
    public ResponseEntity<SeanceCtadpResponse> tenir(
            @PathVariable UUID id,
            @Valid @RequestBody TenirSeanceRequest request) {
        return ResponseEntity.ok(seanceCtadpService.tenir(id, request));
    }
}
