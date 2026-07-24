package gov.bf.ascelc.univers_audits.controller;

import gov.bf.ascelc.univers_audits.service.AuditService;
import gov.bf.ascelc.univers_audits.service.ImageStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/admin/images")
@PreAuthorize("hasRole('ADMIN_DDIC')")
@RequiredArgsConstructor
public class ImageUploadController {

    private final ImageStorageService imageStorageService;

    @Autowired(required = false)
    private AuditService auditService;

    @PostMapping("/upload")
    public ResponseEntity<Map<String, String>> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "context", defaultValue = "portal") String context,
            HttpServletRequest httpRequest,
            @AuthenticationPrincipal Jwt jwt) {

        ImageStorageService.UploadedImage uploaded =
                imageStorageService.upload(file, context);

        if (auditService != null && jwt != null) {
            auditService.logAction(
                    jwt.getSubject(),
                    jwt.getClaimAsString("name"),
                    "ADMIN_DDIC",
                    "UPLOAD_IMAGE", "IMAGE", uploaded.filename(),
                    "Upload : " + uploaded.originalName(),
                    AuditService.extractIp(httpRequest),
                    AuditService.extractUserAgent(httpRequest)
            );
        }

        return ResponseEntity.ok(Map.of(
                "url",          uploaded.url(),
                "filename",     uploaded.filename(),
                "originalName", uploaded.originalName(),
                "size",         String.valueOf(uploaded.size())
        ));
    }

    @GetMapping("/list/{context}")
    public ResponseEntity<List<Map<String, String>>> listImages(
            @PathVariable String context) {

        List<Map<String, String>> images = imageStorageService.list(context).stream()
                .map(i -> Map.of("filename", i.filename(), "url", i.url()))
                .toList();

        return ResponseEntity.ok(images);
    }
}
