package gov.bf.ascelc.univers_audits.controller;

import gov.bf.ascelc.univers_audits.service.PdfExportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/pdf")
@RequiredArgsConstructor
public class PdfController {

    private final PdfExportService pdfExportService;

    @GetMapping("/dossier/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<byte[]> exportDossier(
            @PathVariable UUID id) {

        log.info("Export PDF dossier {}", id);
        byte[] pdf = pdfExportService.exportDossier(id);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"dossier-" + id + ".pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }
}