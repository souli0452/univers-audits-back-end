package gov.bf.ascelc.univers_audits.service;

import gov.bf.ascelc.univers_audits.enums.AttachmentSource;
import gov.bf.ascelc.univers_audits.enums.AttachmentStatus;
import gov.bf.ascelc.univers_audits.enums.AttachmentType;
import gov.bf.ascelc.univers_audits.enums.ModeObtention;
import gov.bf.ascelc.univers_audits.model.entity.Agent;
import gov.bf.ascelc.univers_audits.model.entity.Attachment;
import gov.bf.ascelc.univers_audits.model.entity.Dossier;
import gov.bf.ascelc.univers_audits.model.entity.SectionDossierTravail;
import gov.bf.ascelc.univers_audits.repository.AttachmentRepository;
import gov.bf.ascelc.univers_audits.repository.DossierRepository;
import gov.bf.ascelc.univers_audits.repository.SectionDossierTravailRepository;
import gov.bf.ascelc.univers_audits.shared.exceptions.BusinessException;
import gov.bf.ascelc.univers_audits.shared.exceptions.ResourceNotFoundException;
import gov.bf.ascelc.univers_audits.shared.utils.AccessCodeGenerator;
import gov.bf.ascelc.univers_audits.shared.utils.AgentContextResolver;
import gov.bf.ascelc.univers_audits.shared.utils.DossierAccessGuard;
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
import java.util.concurrent.atomic.AtomicLong;

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
    private final DossierAccessGuard   accessGuard;
    private final AgentContextResolver agentContextResolver;
    private final AccessCodeGenerator  accessCodeGenerator;
    private final SectionDossierTravailRepository sectionDossierTravailRepository;

    private static final int PERSONNE_REMETTANTE_MAX_LENGTH = 255;

    /** Pièces jointes qu'un déposant non authentifié peut verser à son dossier (hors témoignage audio). */
    static final int MAX_PIECES_DEPOT_PUBLIC = 5;
    /** Témoignage audio enregistré au dépôt : un seul par dossier. */
    static final int MAX_AUDIO_DEPOT_PUBLIC = 1;

    @Value("${storage.upload-dir:C:/asce-lc/uploads}")
    private String uploadDir;

    public record UploadedFile(String id, String originalName, String mimeType,
                               long fileSizeBytes, boolean isAudio) {}

    public record AttachmentSummary(String id, String originalName, String mimeType,
                                    Long fileSizeBytes, String uploadedAt,
                                    boolean isAudio, String status,
                                    String description, String source,
                                    String modeObtention, String code) {}

    @Transactional
    public List<UploadedFile> upload(
            String dossierId, List<MultipartFile> files, String accessCode,
            AttachmentSource source, ModeObtention modeObtention, String personneRemettante,
            String sectionId) {
        if (personneRemettante != null && personneRemettante.length() > PERSONNE_REMETTANTE_MAX_LENGTH) {
            throw new BusinessException(
                    "Personne remettante : " + PERSONNE_REMETTANTE_MAX_LENGTH + " caractères maximum");
        }

        Dossier dossier = dossierRepository.findById(UUID.fromString(dossierId))
                .orElseThrow(() -> new BusinessException(
                        "Dossier introuvable: " + dossierId));
        accessGuard.checkAttachmentUploadAccess(dossier, accessCode);

        Path dir = Paths.get(uploadDir, dossierId);
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            log.error("Erreur création dossier upload: {}", e.getMessage(), e);
            throw new BusinessException("Erreur création dossier upload : " + e.getMessage());
        }

        Agent uploader = agentContextResolver.getCurrentAgentOrNull();
        // Un déposant non authentifié (citoyen anonyme via accessCode) ne peut pas
        // s'attribuer une provenance/mode d'obtention d'agent — sans quoi n'importe
        // qui pourrait se déclarer auteur d'une réquisition sur le terrain.
        boolean isAuthenticatedAgent = uploader != null;
        AttachmentSource effectiveSource = isAuthenticatedAgent && source != null
                ? source : AttachmentSource.INITIAL_SUBMISSION;
        ModeObtention effectiveModeObtention = isAuthenticatedAgent && modeObtention != null
                ? modeObtention : ModeObtention.VOLONTAIRE;
        String effectivePersonneRemettante = isAuthenticatedAgent ? personneRemettante : null;
        if (!isAuthenticatedAgent) {
            verifierQuotaDepotPublic(dossier.getId(), files);
        }
        SectionDossierTravail section = resolveSectionForDossierOrThrow(sectionId, dossier);
        AtomicLong nextSequence = new AtomicLong(attachmentRepository.count() + 1);

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
                        .source(effectiveSource)
                        .modeObtention(effectiveModeObtention)
                        .personneRemettante(effectivePersonneRemettante)
                        .uploadedBy(uploader)
                        .code(generateUniqueAttachmentCode(effectiveSource, nextSequence))
                        .section(section)
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
                        att.getStatus().name(),
                        att.getDescription(),
                        att.getSource() != null ? att.getSource().name() : null,
                        att.getModeObtention() != null ? att.getModeObtention().name() : null,
                        att.getCode()))
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

    @Transactional
    public void reclasser(UUID attachmentId, String sectionId) {
        Attachment attachment = attachmentRepository.findById(attachmentId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Piece introuvable : " + attachmentId));

        Dossier dossier = accessGuard.getDossierOrThrow(attachment.getDossier().getId());
        // Lève BusinessException si l'agent n'est ni privilégié ni affecté au dossier.
        accessGuard.checkReadAccess(dossier);

        if (Boolean.TRUE.equals(dossier.getIsConfidential()) && !accessGuard.canSeeConfidential()) {
            throw new BusinessException(
                    "Accès refusé — ce dossier est confidentiel");
        }

        attachment.setSection(resolveSectionForDossierOrThrow(sectionId, dossier));
        attachmentRepository.save(attachment);
    }

    /**
     * Résout la section fournie et vérifie qu'elle appartient bien au même
     * dossier que la pièce jointe concernée — sans cette vérification, rien
     * n'empêche de classer une pièce du dossier A dans une section du
     * dossier B.
     */
    private SectionDossierTravail resolveSectionForDossierOrThrow(String sectionId, Dossier dossier) {
        if (sectionId == null) {
            return null;
        }
        SectionDossierTravail section = sectionDossierTravailRepository
                .findById(UUID.fromString(sectionId))
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Section introuvable : " + sectionId));
        if (!section.getDossier().getId().equals(dossier.getId())) {
            throw new BusinessException(
                    "La section indiquée n'appartient pas à ce dossier");
        }
        return section;
    }

    private String generateUniqueAttachmentCode(AttachmentSource source, AtomicLong nextSequence) {
        String code;
        do {
            code = accessCodeGenerator.generateAttachmentCode(source, nextSequence.getAndIncrement());
        } while (attachmentRepository.existsByCode(code));
        return code;
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

    /**
     * Le formulaire du portail limite le nombre de pièces ; cette règle la rend obligatoire côté serveur,
     * car l'envoi public est appelable sans passer par l'écran. Seules les pièces du dépôt initial comptent :
     * les pièces ajoutées ensuite par les agents n'entrent pas dans le quota du citoyen.
     */
    private void verifierQuotaDepotPublic(UUID dossierId, List<MultipartFile> files) {
        List<Attachment> existantes = attachmentRepository.findByDossierId(dossierId).stream()
                .filter(a -> a.getSource() == AttachmentSource.INITIAL_SUBMISSION)
                .toList();
        long audioExistants = existantes.stream()
                .filter(a -> a.getType() == AttachmentType.AUDIO_EVIDENCE).count();
        long piecesExistantes = existantes.size() - audioExistants;

        long audioNouveaux = files.stream().filter(f -> detectType(
                f.getContentType(), f.getOriginalFilename() != null ? f.getOriginalFilename() : "fichier")
                == AttachmentType.AUDIO_EVIDENCE).count();
        long piecesNouvelles = files.size() - audioNouveaux;

        if (piecesExistantes + piecesNouvelles > MAX_PIECES_DEPOT_PUBLIC) {
            throw new BusinessException("Maximum " + MAX_PIECES_DEPOT_PUBLIC + " pièces jointes par dossier");
        }
        if (audioExistants + audioNouveaux > MAX_AUDIO_DEPOT_PUBLIC) {
            throw new BusinessException("Un seul témoignage audio par dossier");
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
