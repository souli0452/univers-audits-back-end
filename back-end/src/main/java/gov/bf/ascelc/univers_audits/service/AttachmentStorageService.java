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
import gov.bf.ascelc.univers_audits.shared.utils.AssainisseurPieceJointe;
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
import java.util.Locale;
import java.util.Map;
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
            "audio/mp4", "audio/aac",
            "video/mp4", "video/webm", "video/quicktime", "video/x-msvideo"
    );

    /** Noms de type équivalents que les téléphones et navigateurs emploient : ramenés au type de la liste. */
    private static final Map<String, String> TYPES_EQUIVALENTS = Map.of(
            "audio/x-m4a", "audio/mp4", "audio/m4a", "audio/mp4", "audio/x-aac", "audio/aac",
            "audio/opus", "audio/ogg", "audio/x-wav", "audio/wav", "audio/wave", "audio/wav",
            "image/pjpeg", "image/jpeg");

    /** Type déduit du nom quand le navigateur n'en donne aucun (certains envoient « octet-stream »). */
    private static final Map<String, String> TYPE_PAR_EXTENSION = Map.ofEntries(
            Map.entry("jpg", "image/jpeg"), Map.entry("jpeg", "image/jpeg"), Map.entry("png", "image/png"),
            Map.entry("gif", "image/gif"), Map.entry("webp", "image/webp"), Map.entry("pdf", "application/pdf"),
            Map.entry("doc", "application/msword"),
            Map.entry("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
            Map.entry("mp3", "audio/mpeg"), Map.entry("wav", "audio/wav"), Map.entry("ogg", "audio/ogg"),
            Map.entry("opus", "audio/ogg"), Map.entry("m4a", "audio/mp4"), Map.entry("aac", "audio/aac"),
            Map.entry("webm", "audio/webm"), Map.entry("mp4", "video/mp4"), Map.entry("mov", "video/quicktime"),
            Map.entry("avi", "video/x-msvideo"));

    private final AttachmentRepository attachmentRepository;
    private final DossierRepository    dossierRepository;
    private final DossierAccessGuard   accessGuard;
    private final AgentContextResolver agentContextResolver;
    private final AccessCodeGenerator  accessCodeGenerator;
    private final SectionDossierTravailRepository sectionDossierTravailRepository;
    private final AnalyseurAntivirus antivirus;

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
                                    String modeObtention, String code, String analyseAntivirus) {}

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
            // Le détail (chemin du serveur…) reste dans le journal : il ne doit jamais être renvoyé au client.
            throw new BusinessException("Les pièces jointes n'ont pas pu être enregistrées. Réessayez ou contactez l'ASCE-LC.");
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

        // 1) Tout est vérifié et nettoyé AVANT d'écrire quoi que ce soit : un fichier refusé n'est jamais perdu en
        //    silence, et un dépôt à moitié enregistré ne laisse pas de fichiers orphelins.
        List<PieceAEcrire> pieces = preparer(files, isAuthenticatedAgent);

        // 2) Écriture : si un enregistrement échoue, les fichiers déjà écrits sont retirés et l'erreur est signalée.
        List<UploadedFile> saved = new ArrayList<>();
        List<Path> ecrits = new ArrayList<>();
        try {
            for (PieceAEcrire piece : pieces) {
                String ext = "";
                int dotIdx = piece.nomOriginal().lastIndexOf('.');
                if (dotIdx > 0) ext = piece.nomOriginal().substring(dotIdx);

                String storedName = UUID.randomUUID() + ext;
                Path filePath = dir.resolve(storedName);
                Files.write(filePath, piece.octets());
                ecrits.add(filePath);

                Attachment att = Attachment.builder()
                        .dossier(dossier)
                        .originalName(piece.nomOriginal())
                        .storedName(storedName)
                        .filePath(filePath.toString())
                        .mimeType(piece.mime())
                        .fileSizeBytes((long) piece.octets().length)
                        .hashSha256(computeHash(piece.octets()))
                        .type(detectType(piece.mime(), piece.nomOriginal()))
                        .source(effectiveSource)
                        .modeObtention(effectiveModeObtention)
                        .personneRemettante(effectivePersonneRemettante)
                        .uploadedBy(uploader)
                        .code(generateUniqueAttachmentCode(effectiveSource, nextSequence))
                        .section(section)
                        .status(AttachmentStatus.PENDING_VALIDATION)
                        .analyseAntivirus(piece.analyse())
                        .uploadedAt(LocalDateTime.now())
                        .build();

                attachmentRepository.save(att);
                log.info("Fichier sauvegardé: {}", piece.nomOriginal());

                saved.add(new UploadedFile(att.getId().toString(), piece.nomOriginal(), piece.mime(),
                        piece.octets().length, piece.mime().contains("audio")));
            }
        } catch (IOException | RuntimeException e) {
            ecrits.forEach(AttachmentStorageService::supprimerSansErreur);
            log.error("Erreur upload : {}", e.getMessage(), e);
            if (e instanceof BusinessException be) { throw be; }
            throw new BusinessException("Les pièces jointes n'ont pas pu être enregistrées. Réessayez ou contactez l'ASCE-LC.");
        }

        return saved;
    }

    private record PieceAEcrire(String nomOriginal, String mime, byte[] octets,
                               gov.bf.ascelc.univers_audits.enums.AnalyseAntivirus analyse) {}

    /**
     * Contrôle chaque fichier (type accepté, image lisible) et, pour un déposant non authentifié, retire les
     * informations cachées des images. Lève une erreur qui NOMME les fichiers refusés.
     */
    private List<PieceAEcrire> preparer(List<MultipartFile> files, boolean agentAuthentifie) {
        List<String> refuses = new ArrayList<>();
        List<PieceAEcrire> pieces = new ArrayList<>();
        for (MultipartFile file : files) {
            String nom = file.getOriginalFilename() != null ? file.getOriginalFilename() : "fichier";
            String mime = mimeEffectif(file.getContentType(), nom);
            boolean accepte = mime != null && ALLOWED_MIME_TYPES.contains(mime)
                    // Au dépôt public, une image dont on ne sait pas retirer les métadonnées est refusée plutôt que gardée telle quelle.
                    && (agentAuthentifie || !AssainisseurPieceJointe.estImage(mime)
                            || AssainisseurPieceJointe.estImageAssainissable(mime));
            if (!accepte) {
                log.warn("Upload rejeté — type non autorisé '{}' pour {}", file.getContentType(), nom);
                refuses.add(nom);
                continue;
            }
            try {
                byte[] octets = file.getBytes();
                if (!ControleContenuFichier.conforme(mime, octets, agentAuthentifie)) {
                    log.warn("Upload rejeté — contenu non conforme au type '{}' pour {}", mime, nom);
                    refuses.add(nom + " (contenu non conforme à son format)");
                    continue;
                }
                // Sur les octets reçus, avant tout nettoyage
                AnalyseurAntivirus.Verdict verdict = antivirus.analyser(octets);
                if (verdict == AnalyseurAntivirus.Verdict.INFECTE) {
                    refuses.add(nom + " (fichier infecté)");
                    continue;
                }
                if (!agentAuthentifie) {
                    octets = AssainisseurPieceJointe.retirerMetadonnees(mime, octets);
                }
                pieces.add(new PieceAEcrire(nom, mime, octets, verdict == AnalyseurAntivirus.Verdict.SAIN
                        ? gov.bf.ascelc.univers_audits.enums.AnalyseAntivirus.SAIN
                        : gov.bf.ascelc.univers_audits.enums.AnalyseAntivirus.NON_ANALYSE));
            } catch (IllegalArgumentException e) {
                refuses.add(nom + " (image illisible)");
            } catch (IOException e) {
                log.error("Lecture impossible du fichier {} : {}", nom, e.getMessage(), e);
                throw new BusinessException("Lecture impossible du fichier " + nom + ". Réessayez.");
            }
        }
        if (!refuses.isEmpty()) {
            throw new BusinessException("Fichier non accepté : " + String.join(", ", refuses)
                    + ". Aucune pièce n'a été enregistrée ; formats acceptés : PDF, Word, JPG, PNG, MP3, M4A, WAV, MP4, MOV, AVI.");
        }
        return pieces;
    }

    /** Type du fichier sans paramètres (« audio/webm;codecs=opus » → « audio/webm »), ramené au type de la liste. */
    static String mimeEffectif(String declare, String nomFichier) {
        String type = declare == null ? "" : declare.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
        if (type.isEmpty() || type.equals("application/octet-stream")) {
            int point = nomFichier.lastIndexOf('.');
            type = point < 0 ? null : TYPE_PAR_EXTENSION.get(nomFichier.substring(point + 1).toLowerCase(Locale.ROOT));
        }
        return type == null ? null : TYPES_EQUIVALENTS.getOrDefault(type, type);
    }

    private static void supprimerSansErreur(Path fichier) {
        try {
            Files.deleteIfExists(fichier);
        } catch (IOException e) {
            log.warn("Fichier orphelin non supprimé : {}", fichier);
        }
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
                        att.getCode(),
                        att.getAnalyseAntivirus() != null ? att.getAnalyseAntivirus().name() : null))
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
            log.error("Erreur suppression fichier {} : {}", attachment.getFilePath(), e.getMessage(), e);
            throw new BusinessException("Le fichier n'a pas pu être supprimé. Réessayez ou contactez l'administrateur.");
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

        long audioNouveaux = files.stream().filter(f -> {
            String nom = f.getOriginalFilename() != null ? f.getOriginalFilename() : "fichier";
            return detectType(mimeEffectif(f.getContentType(), nom), nom) == AttachmentType.AUDIO_EVIDENCE;
        }).count();
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
                || fn.endsWith(".wav") || fn.endsWith(".ogg")
                || fn.endsWith(".m4a") || fn.endsWith(".aac") || fn.endsWith(".opus"))
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
