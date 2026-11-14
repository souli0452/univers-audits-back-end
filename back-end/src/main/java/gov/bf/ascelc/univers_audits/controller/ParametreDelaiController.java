package gov.bf.ascelc.univers_audits.controller;

import gov.bf.ascelc.univers_audits.model.dto.request.ParametreDelaiRequest;
import gov.bf.ascelc.univers_audits.model.entity.ParametreDelai;
import gov.bf.ascelc.univers_audits.security.permission.AppPermission;
import gov.bf.ascelc.univers_audits.security.permission.HasPermission;
import gov.bf.ascelc.univers_audits.service.ParametreDelaiService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/parametres-delai")
@RequiredArgsConstructor
public class ParametreDelaiController {

    private final ParametreDelaiService parametreDelaiService;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<ParametreDelai>> getActifs() {
        return ResponseEntity.ok(parametreDelaiService.findAllActifs());
    }

    @GetMapping("/admin")
    @HasPermission(AppPermission.PARAMETRE_DELAI_GERER)
    public ResponseEntity<List<ParametreDelai>> getAll() {
        return ResponseEntity.ok(parametreDelaiService.findAll());
    }

    @PutMapping("/{code}")
    @HasPermission(AppPermission.PARAMETRE_DELAI_GERER)
    public ResponseEntity<ParametreDelai> update(
            @PathVariable String code,
            @Valid @RequestBody ParametreDelaiRequest request) {
        return ResponseEntity.ok(parametreDelaiService.update(code, request));
    }
}
