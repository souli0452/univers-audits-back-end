package gov.bf.ascelc.univers_audits.controller;

import gov.bf.ascelc.univers_audits.enums.AttachmentSource;
import gov.bf.ascelc.univers_audits.enums.AttachmentStatus;
import gov.bf.ascelc.univers_audits.enums.AttachmentType;
import gov.bf.ascelc.univers_audits.model.entity.Attachment;
import gov.bf.ascelc.univers_audits.model.entity.Dossier;
import gov.bf.ascelc.univers_audits.repository.AttachmentRepository;
import gov.bf.ascelc.univers_audits.repository.DossierRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@RestController
@RequestMapping("/api/v1/attachments")
@RequiredArgsConstructor
public class AttachmentController {

    private final AttachmentRepository attachmentRepository;
    private final DossierRepository    dossierRepository;

    @Value("${storage.upload-dir:C:/asce-lc/uploads}")
    private String uploadDir;


    @PostMapping("/dossier/{dossierId}")
    public ResponseEntity<?> uploadFiles(
            @PathVariable String dossierId,
            @RequestParam("files") List<MultipartFile> files) {

        log.info("Upload {} fichier(s) pour dossier {}", files.size(), dossierId);

        Dossier dossier = dossierRepository
                .findById(UUID.fromString(dossierId))
                .orElse(null);

        if (dossier == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Dossier introuvable: " + dossierId));
        }

        try {
            Path dir = Paths.get(uploadDir, dossierId);
            Files.createDirectories(dir);

            List<Map<String, Object>> saved = new ArrayList<>();

            for (MultipartFile file : files) {
                try {
                    String originalName = file.getOriginalFilename() != null
                            ? file.getOriginalFilename() : "fichier";

                    String ext = "";
                    int dotIdx = originalName.lastIndexOf('.');
                    if (dotIdx > 0) ext = originalName.substring(dotIdx);

                    String storedName = UUID.randomUUID() + ext;
                    Path filePath = dir.resolve(storedName);
                    byte[] fileBytes = file.getBytes();
                    Files.write(filePath, fileBytes);

                    String hash = computeHash(fileBytes);
                    String mime = file.getContentType() != null
                            ? file.getContentType() : "application/octet-stream";

                    AttachmentType attType = detectType(mime, originalName);

                    Attachment att = Attachment.builder()
                            .dossier(dossier)
                            .originalName(originalName)
                            .storedName(storedName)
                            .filePath(filePath.toString())
                            .mimeType(mime)
                            .fileSizeBytes(file.getSize())
                            .hashSha256(hash)
                            .type(attType)
                            .source(AttachmentSource.INITIAL_SUBMISSION)
                            .status(AttachmentStatus.PENDING_VALIDATION)
                            .uploadedAt(LocalDateTime.now())
                            .build();

                    attachmentRepository.save(att);
                    log.info("Fichier sauvegardé: {}", originalName);

                    saved.add(Map.of(
                            "id",           att.getId().toString(),
                            "originalName", originalName,
                            "mimeType",     mime,
                            "fileSizeBytes", file.getSize(),
                            "isAudio",      mime.contains("audio")
                    ));

                } catch (Exception e) {
                    log.error("Erreur upload fichier {}: {}",
                            file.getOriginalFilename(), e.getMessage(), e);
                }
            }

            return ResponseEntity.ok(Map.of(
                    "uploaded", saved.size(),
                    "files",    saved
            ));

        } catch (IOException e) {
            log.error("Erreur création dossier upload: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/dossier/{dossierId}")
    public ResponseEntity<?> listFiles(@PathVariable String dossierId) {

        List<Map<String, Object>> files = attachmentRepository
                .findByDossierId(UUID.fromString(dossierId))
                .stream()
                .map(att -> Map.<String, Object>of(
                        "id",           att.getId().toString(),
                        "originalName", att.getOriginalName(),
                        "mimeType",     att.getMimeType(),
                        "fileSizeBytes", att.getFileSizeBytes(),
                        "uploadedAt",   att.getUploadedAt() != null
                                ? att.getUploadedAt().toString() : "",
                        "isAudio",      att.getMimeType().contains("audio"),
                        "status",       att.getStatus().name()
                ))
                .toList();

        return ResponseEntity.ok(files);
    }

    @GetMapping("/{attachmentId}/download")
    public ResponseEntity<Resource> download(@PathVariable String attachmentId) {

        Attachment att = attachmentRepository
                .findById(UUID.fromString(attachmentId))
                .orElse(null);

        if (att == null) return ResponseEntity.notFound().build();

        try {
            Path filePath = Paths.get(att.getFilePath());
            Resource resource = new UrlResource(filePath.toUri());

            if (!resource.exists()) {
                log.warn("Fichier introuvable sur disque: {}", att.getFilePath());
                return ResponseEntity.notFound().build();
            }

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(att.getMimeType()))
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "inline; filename=\"" + att.getOriginalName() + "\"")
                    .body(resource);

        } catch (Exception e) {
            log.error("Erreur download: {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    @DeleteMapping("/{attachmentId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> delete(@PathVariable String attachmentId) {

        Attachment att = attachmentRepository
                .findById(UUID.fromString(attachmentId))
                .orElse(null);

        if (att == null) return ResponseEntity.notFound().build();

        try {
            Files.deleteIfExists(Paths.get(att.getFilePath()));
            attachmentRepository.delete(att);
            return ResponseEntity.ok(Map.of("deleted", attachmentId));
        } catch (IOException e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", e.getMessage()));
        }
    }


    private String computeHash(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(data);
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return UUID.randomUUID().toString().replace("-", "");
        }
    }

    private AttachmentType detectType(String contentType, String fileName) {
        if (contentType == null) contentType = "";
        String fn = fileName.toLowerCase();

        if (contentType.contains("audio")
                || fn.endsWith(".mp3") || fn.endsWith(".webm")
                || fn.endsWith(".wav") || fn.endsWith(".ogg"))
            return AttachmentType.AUDIO_EVIDENCE;

        if (contentType.contains("video")
                || fn.endsWith(".mp4") || fn.endsWith(".avi")
                || fn.endsWith(".mov"))
            return AttachmentType.VIDEO;

        if (contentType.contains("image")
                || fn.endsWith(".jpg") || fn.endsWith(".jpeg")
                || fn.endsWith(".png"))
            return AttachmentType.PHOTO;

        return AttachmentType.DOCUMENT;
    }
}