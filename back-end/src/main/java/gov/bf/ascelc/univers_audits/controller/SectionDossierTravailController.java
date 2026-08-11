package gov.bf.ascelc.univers_audits.controller;

import gov.bf.ascelc.univers_audits.model.dto.request.OrganisationDetailRequest;
import gov.bf.ascelc.univers_audits.model.dto.request.SectionDetailCreateRequest;
import gov.bf.ascelc.univers_audits.service.SectionDossierTravailService;
import gov.bf.ascelc.univers_audits.service.SectionDossierTravailService.SectionDossierTravailResponse;
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
@RequestMapping("/api/v1/dossiers/{dossierId}/dossier-travail")
@RequiredArgsConstructor
public class SectionDossierTravailController {

    private static final String READ_ROLES =
            "hasAnyRole('AGENT_BRPD','CONSEILLER_JURIDIQUE','MEMBRE_CTADP',"
                    + "'CGEA','CGE','CONTROLEUR_ETAT','ADMIN_DDIC')";

    private static final String WRITE_ROLES =
            "hasAnyRole('CONTROLEUR_ETAT','AGENT_BRPD','ADMIN_DDIC')";

    private final SectionDossierTravailService sectionDossierTravailService;

    @PreAuthorize(READ_ROLES)
    @GetMapping("/sections")
    public ResponseEntity<List<SectionDossierTravailResponse>> listerSections(
            @PathVariable String dossierId) {
        return ResponseEntity.ok(
                sectionDossierTravailService.listerSections(UUID.fromString(dossierId)));
    }

    @PreAuthorize(WRITE_ROLES)
    @PostMapping("/organisation-detail")
    public ResponseEntity<?> definirOrganisationDetail(
            @PathVariable String dossierId,
            @Valid @RequestBody OrganisationDetailRequest request) {
        sectionDossierTravailService.definirOrganisationDetail(
                UUID.fromString(dossierId), request.getOrganisationDetail());
        return ResponseEntity.ok().build();
    }

    @PreAuthorize(WRITE_ROLES)
    @PostMapping("/sections")
    public ResponseEntity<SectionDossierTravailResponse> creerSectionDetail(
            @PathVariable String dossierId,
            @Valid @RequestBody SectionDetailCreateRequest request) {
        var section = sectionDossierTravailService.creerSectionDetail(
                UUID.fromString(dossierId), request.getLibelle());
        return ResponseEntity.ok(new SectionDossierTravailResponse(
                section.getId(), section.getType(), section.getLibelle(), 0));
    }
}
