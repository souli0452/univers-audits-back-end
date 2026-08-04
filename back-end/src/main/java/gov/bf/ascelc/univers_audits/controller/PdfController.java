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

    @GetMapping("/recepisse/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<byte[]> exportRecepisse(
            @PathVariable UUID id) {

        log.info("Export récépissé dossier {}", id);
        byte[] pdf = pdfExportService.exportRecepisse(id);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"recepisse-" + id + ".pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    @GetMapping("/accuse-reception/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<byte[]> exportAccuseReception(
            @PathVariable UUID id) {

        log.info("Export accusé de réception dossier {}", id);
        byte[] pdf = pdfExportService.exportAccuseReception(id);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"accuse-reception-" + id + ".pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    @GetMapping("/reponse-motivee/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<byte[]> exportReponseMotivee(
            @PathVariable UUID id) {

        log.info("Export réponse motivée dossier {}", id);
        byte[] pdf = pdfExportService.exportReponseMotivee(id);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"reponse-motivee-" + id + ".pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }
}