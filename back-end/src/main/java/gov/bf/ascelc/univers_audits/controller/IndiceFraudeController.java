package gov.bf.ascelc.univers_audits.controller;

import gov.bf.ascelc.univers_audits.model.dto.request.IndiceFraudeRequest;
import gov.bf.ascelc.univers_audits.model.entity.IndiceFraude;
import gov.bf.ascelc.univers_audits.security.permission.AppPermission;
import gov.bf.ascelc.univers_audits.security.permission.HasPermission;
import gov.bf.ascelc.univers_audits.service.IndiceFraudeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/indices-fraude")
@RequiredArgsConstructor
public class IndiceFraudeController {

    private final IndiceFraudeService indiceFraudeService;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<IndiceFraude>> getActifs(
            @RequestParam(required = false) String categorie) {
        return ResponseEntity.ok(indiceFraudeService.findAllActifs(categorie));
    }

    @GetMapping("/admin")
    @HasPermission(AppPermission.INDICE_FRAUDE_GERER)
    public ResponseEntity<List<IndiceFraude>> getAll() {
        return ResponseEntity.ok(indiceFraudeService.findAll());
    }

    @PostMapping
    @HasPermission(AppPermission.INDICE_FRAUDE_GERER)
    public ResponseEntity<IndiceFraude> create(
            @Valid @RequestBody IndiceFraudeRequest request) {
        return ResponseEntity.ok(indiceFraudeService.create(request));
    }

    @PutMapping("/{code}")
    @HasPermission(AppPermission.INDICE_FRAUDE_GERER)
    public ResponseEntity<IndiceFraude> update(
            @PathVariable String code,
            @Valid @RequestBody IndiceFraudeRequest request) {
        return ResponseEntity.ok(indiceFraudeService.update(code, request));
    }
}
