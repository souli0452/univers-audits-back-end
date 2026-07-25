package gov.bf.ascelc.univers_audits.controller;

import gov.bf.ascelc.univers_audits.model.dto.request.TypeInfractionRequest;
import gov.bf.ascelc.univers_audits.model.entity.TypeInfraction;
import gov.bf.ascelc.univers_audits.service.TypeInfractionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/types-infraction")
@RequiredArgsConstructor
public class TypeInfractionController {

    private final TypeInfractionService typeInfractionService;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<TypeInfraction>> getActifs() {
        return ResponseEntity.ok(typeInfractionService.findAllActifs());
    }

    @GetMapping("/admin")
    @PreAuthorize("hasAnyRole('ADMIN_DDIC','CGEA')")
    public ResponseEntity<List<TypeInfraction>> getAll() {
        return ResponseEntity.ok(typeInfractionService.findAll());
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN_DDIC','CGEA')")
    public ResponseEntity<TypeInfraction> create(
            @Valid @RequestBody TypeInfractionRequest request) {
        return ResponseEntity.ok(typeInfractionService.create(request));
    }

    @PutMapping("/{code}")
    @PreAuthorize("hasAnyRole('ADMIN_DDIC','CGEA')")
    public ResponseEntity<TypeInfraction> update(
            @PathVariable String code,
            @Valid @RequestBody TypeInfractionRequest request) {
        return ResponseEntity.ok(typeInfractionService.update(code, request));
    }
}
