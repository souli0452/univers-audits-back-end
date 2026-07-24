package gov.bf.ascelc.univers_audits.service;

import gov.bf.ascelc.univers_audits.enums.AttachmentSource;
import gov.bf.ascelc.univers_audits.enums.AttachmentStatus;
import gov.bf.ascelc.univers_audits.enums.AttachmentType;
import gov.bf.ascelc.univers_audits.model.entity.Attachment;
import gov.bf.ascelc.univers_audits.model.entity.Dossier;
import gov.bf.ascelc.univers_audits.repository.AttachmentRepository;
import gov.bf.ascelc.univers_audits.repository.DossierRepository;
import gov.bf.ascelc.univers_audits.shared.exceptions.BusinessException;
import gov.bf.ascelc.univers_audits.shared.exceptions.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AttachmentStorageService {

    /** Types MIME acceptés en pièce jointe — tout le reste est rejeté. */
    private static final Set<String> ALLOWED_MIME_TYPES = Set.of(
            "image/jpeg", "image/jpg", "image/png", "image/gif", "image/webp",
            "application/pdf",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "text/plain", "text/csv",
            "audio/mpeg", "audio/mp3", "audio/wav", "audio/webm", "audio/ogg",
            "video/mp4", "video/webm", "video/quicktime", "video/x-msvideo"
    );

    private final AttachmentRepository attachmentRepository;
    private final DossierRepository    dossierRepository;

    @Value("${storage.upload-dir:C:/asce-lc/uploads}")
    private String uploadDir;

    public record UploadedFile(String id, String originalName, String mimeType,
                               long fileSizeBytes, boolean isAudio) {}

    public record AttachmentSummary(String id, String originalName, String mimeType,
                                    Long fileSizeBytes, String uploadedAt,
                                    boolean isAudio, String status) {}

    @Transactional
    public List<UploadedFile> upload(String dossierId, List<MultipartFile> files) {
        Dossier dossier = dossierRepository.findById(UUID.fromString(dossierId))
                .orElseThrow(() -> new BusinessException(
                        "Dossier introuvable: " + dossierId));

        Path dir = Paths.get(uploadDir, dossierId);
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            log.error("Erreur création dossier upload: {}", e.getMessage(), e);
            throw new BusinessException("Erreur création dossier upload : " + e.getMessage());
        }

        List<UploadedFile> saved = new ArrayList<>();

        for (MultipartFile file : files) {
            try {
                String originalName = file.getOriginalFilename() != null
                        ? file.getOriginalFilename() : "fichier";

                String mime = file.getContentType() != null
                        ? file.getContentType() : "application/octet-stream";

                if (!ALLOWED_MIME_TYPES.contains(mime)) {
                    log.warn("Upload rejeté — type non autorisé '{}' pour {}",
                            mime, file.getOriginalFilename());
                    continue;
                }

                String ext = "";
                int dotIdx = originalName.lastIndexOf('.');
                if (dotIdx > 0) ext = originalName.substring(dotIdx);

                String storedName = UUID.randomUUID() + ext;
                Path filePath = dir.resolve(storedName);
                byte[] fileBytes = file.getBytes();
                Files.write(filePath, fileBytes);

                String hash = computeHash(fileBytes);
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

                saved.add(new UploadedFile(att.getId().toString(), originalName, mime,
                        file.getSize(), mime.contains("audio")));

            } catch (Exception e) {
                log.error("Erreur upload fichier {}: {}",
                        file.getOriginalFilename(), e.getMessage(), e);
            }
        }

        return saved;
    }

    public List<AttachmentSummary> listByDossier(UUID dossierId) {
        return attachmentRepository.findByDossierId(dossierId).stream()
                .map(att -> new AttachmentSummary(
                        att.getId().toString(),
                        att.getOriginalName(),
                        att.getMimeType(),
                        att.getFileSizeBytes(),
                        att.getUploadedAt() != null ? att.getUploadedAt().toString() : "",
                        att.getMimeType() != null && att.getMimeType().contains("audio"),
                        att.getStatus().name()))
                .toList();
    }

    public Attachment getOrThrow(UUID attachmentId) {
        return attachmentRepository.findById(attachmentId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Pièce jointe introuvable : " + attachmentId));
    }

    @Transactional
    public void delete(Attachment attachment) {
        try {
            Files.deleteIfExists(Paths.get(attachment.getFilePath()));
        } catch (IOException e) {
            throw new BusinessException("Erreur suppression fichier : " + e.getMessage());
        }
        attachmentRepository.delete(attachment);
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
