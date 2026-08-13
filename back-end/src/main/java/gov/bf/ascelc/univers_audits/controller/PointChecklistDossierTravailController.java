package gov.bf.ascelc.univers_audits.controller;

import gov.bf.ascelc.univers_audits.model.dto.request.PointChecklistDossierTravailRequest;
import gov.bf.ascelc.univers_audits.model.entity.PointChecklistDossierTravail;
import gov.bf.ascelc.univers_audits.service.PointChecklistDossierTravailService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/points-checklist-dossier-travail")
@RequiredArgsConstructor
public class PointChecklistDossierTravailController {

    private final PointChecklistDossierTravailService service;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<PointChecklistDossierTravail>> getActifs() {
        return ResponseEntity.ok(service.findAllActifs());
    }

    @GetMapping("/admin")
    @PreAuthorize("hasAnyRole('ADMIN_DDIC','CGEA')")
    public ResponseEntity<List<PointChecklistDossierTravail>> getAll() {
        return ResponseEntity.ok(service.findAll());
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN_DDIC','CGEA')")
    public ResponseEntity<PointChecklistDossierTravail> create(
            @Valid @RequestBody PointChecklistDossierTravailRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @PutMapping("/{code}")
    @PreAuthorize("hasAnyRole('ADMIN_DDIC','CGEA')")
    public ResponseEntity<PointChecklistDossierTravail> update(
            @PathVariable String code,
            @Valid @RequestBody PointChecklistDossierTravailRequest request) {
        return ResponseEntity.ok(service.update(code, request));
    }
}
