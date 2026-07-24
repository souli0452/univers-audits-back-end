package gov.bf.ascelc.univers_audits.service;

import gov.bf.ascelc.univers_audits.shared.exceptions.BusinessException;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
public class ImageStorageService {

    @Value("${storage.upload-dir:C:/asce-lc/uploads}")
    private String uploadDir;

    @Value("${app.api-base-url:http://localhost:8081}")
    private String apiBaseUrl;

    private static final Set<String> ALLOWED_TYPES = Set.of(
            "image/jpeg", "image/jpg", "image/png",
            "image/gif", "image/webp", "image/svg+xml"
    );
    private static final long MAX_SIZE = 5 * 1024 * 1024;

    public record UploadedImage(String url, String filename, String originalName, long size) {}

    public record ImageInfo(String filename, String url) {}

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

    public UploadedImage upload(MultipartFile file, String context) {
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_TYPES.contains(contentType)) {
            throw new BusinessException(
                    "Type non autorisé. Formats : JPG, PNG, GIF, WEBP, SVG");
        }
        if (file.getSize() > MAX_SIZE) {
            throw new BusinessException("Fichier trop volumineux. Maximum : 5 Mo");
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

            return new UploadedImage(publicUrl, uniqueName,
                    originalName != null ? originalName : "", file.getSize());

        } catch (IOException e) {
            log.error("[ImageUpload] Erreur : {}", e.getMessage(), e);
            throw new BusinessException(
                    "Erreur lors de l'enregistrement : " + e.getMessage());
        }
    }

    public List<ImageInfo> list(String context) {
        try {
            Path dir = Paths.get(uploadDir, "portal", context);
            if (!Files.exists(dir)) return List.of();

            try (var stream = Files.list(dir)) {
                return stream
                        .filter(p -> !Files.isDirectory(p))
                        .map(p -> new ImageInfo(
                                p.getFileName().toString(),
                                apiBaseUrl + "/api/v1/public/images/portal/"
                                        + context + "/" + p.getFileName()))
                        .toList();
            }
        } catch (IOException e) {
            return List.of();
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
