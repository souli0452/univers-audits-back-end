package gov.bf.ascelc.univers_audits.controller;

import gov.bf.ascelc.univers_audits.service.AuditService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

@Slf4j
@RestController
@RequestMapping("/api/v1/admin/images")
@PreAuthorize("hasRole('ADMIN_DDIC')")
public class ImageUploadController {

    @Value("${storage.upload-dir:C:/asce-lc/uploads}")
    private String uploadDir;

    @Value("${app.api-base-url:http://localhost:8081}")
    private String apiBaseUrl;

    @Autowired(required = false)
    private AuditService auditService;

    private static final Set<String> ALLOWED_TYPES = Set.of(
            "image/jpeg", "image/jpg", "image/png",
            "image/gif", "image/webp", "image/svg+xml"
    );
    private static final long MAX_SIZE = 5 * 1024 * 1024;

    @PostConstruct
    public void init() {
        try {
            Files.createDirectories(Paths.get(uploadDir, "portal", "banner"));
            Files.createDirectories(Paths.get(uploadDir, "portal", "logo"));
            Files.createDirectories(Paths.get(uploadDir, "portal", "portal"));
            log.info("[ImageUpload] Dossiers créés : {}/portal/", uploadDir);
        } catch (IOException e) {
            log.warn("[ImageUpload] Impossible de créer les dossiers : {}", e.getMessage());
        }
    }

    @PostMapping("/upload")
    public ResponseEntity<Map<String, String>> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "context", defaultValue = "portal") String context,
            HttpServletRequest httpRequest,
            @AuthenticationPrincipal Jwt jwt) {

        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_TYPES.contains(contentType)) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Type non autorisé. Formats : JPG, PNG, GIF, WEBP, SVG"
            ));
        }

        if (file.getSize() > MAX_SIZE) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Fichier trop volumineux. Maximum : 5 Mo"
            ));
        }

        try {
            Path destDir = Paths.get(uploadDir, "portal", context);
            Files.createDirectories(destDir);

            String originalName = file.getOriginalFilename();
            String extension    = getExtension(originalName);
            String uniqueName   = UUID.randomUUID().toString().substring(0, 8)
                    + "_" + sanitize(originalName) + extension;

            Files.copy(file.getInputStream(),
                    destDir.resolve(uniqueName),
                    StandardCopyOption.REPLACE_EXISTING);

            String publicUrl = apiBaseUrl + "/api/v1/public/images/portal/"
                    + context + "/" + uniqueName;

            log.info("[ImageUpload] {} → {}", originalName, publicUrl);

            if (auditService != null && jwt != null) {
                auditService.logAction(
                        jwt.getSubject(),
                        jwt.getClaimAsString("name"),
                        "ADMIN_DDIC",
                        "UPLOAD_IMAGE", "IMAGE", uniqueName,
                        "Upload : " + originalName,
                        httpRequest
                );
            }

            return ResponseEntity.ok(Map.of(
                    "url",          publicUrl,
                    "filename",     uniqueName,
                    "originalName", originalName != null ? originalName : "",
                    "size",         String.valueOf(file.getSize())
            ));

        } catch (IOException e) {
            log.error("[ImageUpload] Erreur : {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", "Erreur lors de l'enregistrement : " + e.getMessage()
            ));
        }
    }

    @GetMapping("/list/{context}")
    public ResponseEntity<List<Map<String, String>>> listImages(
            @PathVariable String context) {
        try {
            Path dir = Paths.get(uploadDir, "portal", context);
            if (!Files.exists(dir)) return ResponseEntity.ok(List.of());

            List<Map<String, String>> images = Files.list(dir)
                    .filter(p -> !Files.isDirectory(p))
                    .map(p -> Map.of(
                            "filename", p.getFileName().toString(),
                            "url", apiBaseUrl + "/api/v1/public/images/portal/"
                                    + context + "/" + p.getFileName()
                    ))
                    .toList();

            return ResponseEntity.ok(images);
        } catch (IOException e) {
            return ResponseEntity.ok(List.of());
        }
    }

    private String getExtension(String filename) {
        if (filename == null || !filename.contains(".")) return ".jpg";
        return filename.substring(filename.lastIndexOf(".")).toLowerCase();
    }

    private String sanitize(String filename) {
        if (filename == null) return "image";
        String name = filename.contains(".")
                ? filename.substring(0, filename.lastIndexOf("."))
                : filename;
        return name.replaceAll("[^a-zA-Z0-9_-]", "_").toLowerCase();
    }
}