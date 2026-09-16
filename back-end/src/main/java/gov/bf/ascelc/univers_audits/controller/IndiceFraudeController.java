package gov.bf.ascelc.univers_audits.controller;

import gov.bf.ascelc.univers_audits.model.dto.request.IndiceFraudeRequest;
import gov.bf.ascelc.univers_audits.model.entity.IndiceFraude;
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
    @PreAuthorize("hasAnyRole('ADMIN_DDIC','CGEA')")
    public ResponseEntity<List<IndiceFraude>> getAll() {
        return ResponseEntity.ok(indiceFraudeService.findAll());
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN_DDIC','CGEA')")
    public ResponseEntity<IndiceFraude> create(
            @Valid @RequestBody IndiceFraudeRequest request) {
        return ResponseEntity.ok(indiceFraudeService.create(request));
    }

    @PutMapping("/{code}")
    @PreAuthorize("hasAnyRole('ADMIN_DDIC','CGEA')")
    public ResponseEntity<IndiceFraude> update(
            @PathVariable String code,
            @Valid @RequestBody IndiceFraudeRequest request) {
        return ResponseEntity.ok(indiceFraudeService.update(code, request));
    }
}
