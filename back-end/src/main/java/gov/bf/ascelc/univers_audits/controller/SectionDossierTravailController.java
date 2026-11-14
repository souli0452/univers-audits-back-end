package gov.bf.ascelc.univers_audits.controller;

import gov.bf.ascelc.univers_audits.model.dto.request.OrganisationDetailRequest;
import gov.bf.ascelc.univers_audits.model.dto.request.SectionDetailCreateRequest;
import gov.bf.ascelc.univers_audits.security.permission.AppPermission;
import gov.bf.ascelc.univers_audits.security.permission.HasPermission;
import gov.bf.ascelc.univers_audits.service.SectionDossierTravailService;
import gov.bf.ascelc.univers_audits.service.SectionDossierTravailService.SectionDossierTravailResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/dossiers/{dossierId}/dossier-travail")
@RequiredArgsConstructor
public class SectionDossierTravailController {

    private final SectionDossierTravailService sectionDossierTravailService;

    @HasPermission(AppPermission.SECTION_DOSSIER_TRAVAIL_VOIR)
    @GetMapping("/sections")
    public ResponseEntity<List<SectionDossierTravailResponse>> listerSections(
            @PathVariable String dossierId) {
        return ResponseEntity.ok(
                sectionDossierTravailService.listerSections(UUID.fromString(dossierId)));
    }

    @HasPermission(AppPermission.SECTION_DOSSIER_TRAVAIL_GERER)
    @PostMapping("/organisation-detail")
    public ResponseEntity<?> definirOrganisationDetail(
            @PathVariable String dossierId,
            @Valid @RequestBody OrganisationDetailRequest request) {
        sectionDossierTravailService.definirOrganisationDetail(
                UUID.fromString(dossierId), request.getOrganisationDetail());
        return ResponseEntity.ok().build();
    }

    @HasPermission(AppPermission.SECTION_DOSSIER_TRAVAIL_GERER)
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
