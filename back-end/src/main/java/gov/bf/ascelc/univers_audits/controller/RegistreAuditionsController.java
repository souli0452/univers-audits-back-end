package gov.bf.ascelc.univers_audits.controller;

import gov.bf.ascelc.univers_audits.model.dto.response.RegistreAuditionEntryResponse;
import gov.bf.ascelc.univers_audits.service.RegistreAuditionsService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/registre-auditions")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('CGEA','ADMIN_DDIC')")
public class RegistreAuditionsController {

    private final RegistreAuditionsService registreAuditionsService;

    @GetMapping
    public ResponseEntity<Page<RegistreAuditionEntryResponse>> findAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(
                registreAuditionsService.findAll(PageRequest.of(page, size)));
    }
}
