package gov.bf.ascelc.univers_audits.controller;

import gov.bf.ascelc.univers_audits.service.AccessMatrixService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Consultation des accès par rôle (lecture seule, calculée depuis les contrôles d'accès du code). */
@RestController
@RequestMapping("/api/v1/admin/access-matrix")
@PreAuthorize("hasAnyRole('ADMIN_DDIC','CGE','CGEA')")
@RequiredArgsConstructor
public class AccessMatrixController {

    private final AccessMatrixService accessMatrixService;

    @GetMapping
    public ResponseEntity<AccessMatrixService.Matrice> matrice() {
        return ResponseEntity.ok(accessMatrixService.matrice());
    }
}
