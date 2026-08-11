package gov.bf.ascelc.univers_audits.controller;

import gov.bf.ascelc.univers_audits.enums.AttachmentSource;
import gov.bf.ascelc.univers_audits.enums.ModeObtention;
import gov.bf.ascelc.univers_audits.model.entity.Attachment;
import gov.bf.ascelc.univers_audits.model.entity.Dossier;
import gov.bf.ascelc.univers_audits.service.AttachmentStorageService;
import gov.bf.ascelc.univers_audits.shared.exceptions.BusinessException;
import gov.bf.ascelc.univers_audits.shared.utils.DossierAccessGuard;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/attachments")
@RequiredArgsConstructor
public class AttachmentController {

    private static final String DOSSIER_READ_ROLES =
            "hasAnyRole('AGENT_BRPD','CONSEILLER_JURIDIQUE','MEMBRE_CTADP',"
                    + "'CGEA','CGE','CONTROLEUR_ETAT','ADMIN_DDIC')";

    private final AttachmentStorageService attachmentStorageService;
    private final DossierAccessGuard       accessGuard;

    @PostMapping("/dossier/{dossierId}")
    public ResponseEntity<?> uploadFiles(
            @PathVariable String dossierId,
            @RequestParam("files") List<MultipartFile> files,
            @RequestParam(value = "accessCode", required = false) String accessCode,
            @RequestParam(value = "source", required = false) AttachmentSource source,
            @RequestParam(value = "modeObtention", required = false) ModeObtention modeObtention,
            @RequestParam(value = "personneRemettante", required = false) String personneRemettante,
            @RequestParam(value = "sectionId", required = false) String sectionId) {

        log.info("Upload {} fichier(s) pour dossier {}", files.size(), dossierId);

        List<AttachmentStorageService.UploadedFile> saved =
                attachmentStorageService.upload(
                        dossierId, files, accessCode, source, modeObtention,
                        personneRemettante, sectionId);

        return ResponseEntity.ok(Map.of(
                "uploaded", saved.size(),
                "files",    saved
        ));
    }

    @GetMapping("/dossier/{dossierId}")
    @PreAuthorize(DOSSIER_READ_ROLES)
    public ResponseEntity<?> listFiles(@PathVariable String dossierId) {

        UUID id = UUID.fromString(dossierId);
        Dossier dossier = accessGuard.getDossierOrThrow(id);
        // Lève BusinessException si l'agent n'est ni privilégié ni affecté au dossier.
        accessGuard.checkReadAccess(dossier);

        // Un dossier confidentiel masque entièrement ses pièces jointes aux rôles
        // non habilités — même comportement que DossierServiceImpl.maskSensitiveData.
        if (Boolean.TRUE.equals(dossier.getIsConfidential()) && !accessGuard.canSeeConfidential()) {
            return ResponseEntity.ok(List.of());
        }

        return ResponseEntity.ok(attachmentStorageService.listByDossier(id));
    }

    @GetMapping("/{attachmentId}/download")
    @PreAuthorize(DOSSIER_READ_ROLES)
    public ResponseEntity<Resource> download(@PathVariable String attachmentId) {

        Attachment att = attachmentStorageService.getOrThrow(UUID.fromString(attachmentId));
        Dossier dossier = accessGuard.getDossierOrThrow(att.getDossier().getId());
        // Lève BusinessException si l'agent n'est ni privilégié ni affecté au dossier.
        accessGuard.checkReadAccess(dossier);

        if (Boolean.TRUE.equals(dossier.getIsConfidential()) && !accessGuard.canSeeConfidential()) {
            throw new BusinessException(
                    "Accès refusé — ce dossier est confidentiel");
        }

        try {
            Path filePath = Paths.get(att.getFilePath());
            Resource resource = new UrlResource(filePath.toUri());

            if (!resource.exists()) {
                log.warn("Fichier introuvable sur disque: {}", att.getFilePath());
                return ResponseEntity.notFound().build();
            }

            // Seules les images sont affichées inline ; tout le reste est forcé en
            // téléchargement pour empêcher un rendu actif (HTML/JS) dans le navigateur.
            boolean inlineable = att.getMimeType() != null
                    && att.getMimeType().startsWith("image/");
            String disposition = (inlineable ? "inline" : "attachment")
                    + "; filename=\"" + sanitizeFilename(att.getOriginalName()) + "\"";

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(att.getMimeType()))
                    .header(HttpHeaders.CONTENT_DISPOSITION, disposition)
                    .header("X-Content-Type-Options", "nosniff")
                    .body(resource);

        } catch (Exception e) {
            log.error("Erreur download: {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    @DeleteMapping("/{attachmentId}")
    @PreAuthorize("hasAnyRole('AGENT_BRPD','CGEA','ADMIN_DDIC')")
    public ResponseEntity<?> delete(@PathVariable String attachmentId) {

        Attachment att = attachmentStorageService.getOrThrow(UUID.fromString(attachmentId));
        Dossier dossier = accessGuard.getDossierOrThrow(att.getDossier().getId());
        // Lève BusinessException si l'agent n'est ni privilégié ni affecté au dossier.
        accessGuard.checkReadAccess(dossier);

        if (Boolean.TRUE.equals(dossier.getIsConfidential()) && !accessGuard.canSeeConfidential()) {
            throw new BusinessException(
                    "Accès refusé — ce dossier est confidentiel");
        }

        attachmentStorageService.delete(att);
        return ResponseEntity.ok(Map.of("deleted", attachmentId));
    }

    @PatchMapping("/{attachmentId}/section")
    public ResponseEntity<?> reclasser(
            @PathVariable String attachmentId,
            @RequestBody Map<String, String> body) {
        attachmentStorageService.reclasser(
                UUID.fromString(attachmentId), body.get("sectionId"));
        return ResponseEntity.ok().build();
    }

    private String sanitizeFilename(String name) {
        if (name == null) return "fichier";
        return name.replaceAll("[\"\\r\\n]", "_");
    }
}
