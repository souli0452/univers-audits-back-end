package gov.bf.ascelc.univers_audits.controller;

import gov.bf.ascelc.univers_audits.model.dto.response.ComplementRequestResponse;
import gov.bf.ascelc.univers_audits.model.dto.response.ComplementSubmissionResponse;
import gov.bf.ascelc.univers_audits.service.AuditService;
import gov.bf.ascelc.univers_audits.service.ComplementPublicService;
import gov.bf.ascelc.univers_audits.shared.utils.ApiUrls;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Routes publiques (sans authentification) : le code de suivi sert de preuve. Elles sont
 * déclarées en permitAll dans SecurityConfig et limitées par RateLimitFilter.
 */
@RestController
@RequestMapping(ApiUrls.DOSSIERS + "/public/complement")
@RequiredArgsConstructor
public class ComplementPublicController {

    private final ComplementPublicService service;

    @GetMapping("/{accessCode}")
    public ResponseEntity<ComplementRequestResponse> getComplementRequest(
            @PathVariable String accessCode) {
        return ResponseEntity.ok(service.getComplementRequest(accessCode));
    }

    @PostMapping(value = "/{accessCode}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ComplementSubmissionResponse> submitComplement(
            @PathVariable String accessCode,
            @RequestParam(value = "message", required = false) String message,
            @RequestParam(value = "files", required = false) List<MultipartFile> files,
            HttpServletRequest request) {
        return ResponseEntity.ok(service.submitComplement(
                accessCode, message, files, AuditService.extractIp(request)));
    }
}
