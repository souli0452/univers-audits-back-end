package gov.bf.ascelc.univers_audits.controller;

import gov.bf.ascelc.univers_audits.model.dto.request.JourFerieRequest;
import gov.bf.ascelc.univers_audits.model.entity.JourFerie;
import gov.bf.ascelc.univers_audits.service.JourFerieService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/jours-feries")
@RequiredArgsConstructor
public class JourFerieController {

    private final JourFerieService jourFerieService;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<JourFerie>> getActifs() {
        return ResponseEntity.ok(jourFerieService.findAllActifs());
    }

    @GetMapping("/admin")
    @PreAuthorize("hasAnyRole('ADMIN_DDIC','CGEA')")
    public ResponseEntity<List<JourFerie>> getAll() {
        return ResponseEntity.ok(jourFerieService.findAll());
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN_DDIC','CGEA')")
    public ResponseEntity<JourFerie> create(
            @Valid @RequestBody JourFerieRequest request) {
        return ResponseEntity.ok(jourFerieService.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN_DDIC','CGEA')")
    public ResponseEntity<JourFerie> update(
            @PathVariable UUID id,
            @Valid @RequestBody JourFerieRequest request) {
        return ResponseEntity.ok(jourFerieService.update(id, request));
    }
}
