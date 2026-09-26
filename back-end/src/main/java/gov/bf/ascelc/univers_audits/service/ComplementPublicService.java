package gov.bf.ascelc.univers_audits.service;

import gov.bf.ascelc.univers_audits.enums.DossierStatus;
import gov.bf.ascelc.univers_audits.enums.NotificationChannel;
import gov.bf.ascelc.univers_audits.enums.NotificationType;
import gov.bf.ascelc.univers_audits.enums.ObservationType;
import gov.bf.ascelc.univers_audits.model.dto.response.ComplementRequestResponse;
import gov.bf.ascelc.univers_audits.model.dto.response.ComplementSubmissionResponse;
import gov.bf.ascelc.univers_audits.model.entity.Dossier;
import gov.bf.ascelc.univers_audits.model.entity.Notification;
import gov.bf.ascelc.univers_audits.model.entity.Observation;
import gov.bf.ascelc.univers_audits.repository.DossierRepository;
import gov.bf.ascelc.univers_audits.repository.NotificationRepository;
import gov.bf.ascelc.univers_audits.repository.ObservationRepository;
import gov.bf.ascelc.univers_audits.shared.exceptions.BusinessException;
import gov.bf.ascelc.univers_audits.shared.exceptions.ConflictException;
import gov.bf.ascelc.univers_audits.shared.exceptions.ResourceNotFoundException;
import gov.bf.ascelc.univers_audits.shared.utils.DossierAuditRecorder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Réponse d'un déclarant à une demande de complément, depuis le portail public : le code de
 * suivi (accessCode) sert de preuve, comme pour le suivi public.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ComplementPublicService {

    static final int MAX_FILES = 5;
    static final int MESSAGE_MAX_LENGTH = 2000;
    static final String NOT_WAITING = "Aucun complément n'est attendu pour ce dossier";
    static final String DECLARANT_LABEL = "Déclarant (via le portail)";
    private static final DateTimeFormatter DAY =
            DateTimeFormatter.ofPattern("dd/MM/yyyy").withZone(ZoneOffset.UTC);

    private final DossierRepository dossierRepository;
    private final ObservationRepository observationRepository;
    private final NotificationRepository notificationRepository;
    private final AttachmentStorageService attachmentStorageService;
    private final DossierAuditRecorder auditRecorder;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public ComplementRequestResponse getComplementRequest(String accessCode) {
        return getComplementRequest(accessCode, Instant.now());
    }

    ComplementRequestResponse getComplementRequest(String accessCode, Instant now) {
        Dossier dossier = findWaitingDossier(accessCode);
        Observation request = lastRequest(dossier);
        Instant deadline = dossier.getAdditionalInfoDeadline();
        return new ComplementRequestResponse(
                dossier.getStatus().name(),
                request.getContent(),
                request.getCreatedAt(),
                deadline,
                deadline != null && now.isAfter(deadline));
    }

    @Transactional
    public ComplementSubmissionResponse submitComplement(String accessCode, String message,
                                                         List<MultipartFile> files,
                                                         String ipAddress) {
        return submitComplement(accessCode, message, files, ipAddress, Instant.now());
    }

    ComplementSubmissionResponse submitComplement(String accessCode, String message,
                                                  List<MultipartFile> files,
                                                  String ipAddress, Instant now) {
        String text = message == null ? "" : message.trim();
        List<MultipartFile> attached = files == null ? List.of()
                : files.stream().filter(f -> f != null && !f.isEmpty()).toList();

        if (text.isEmpty() && attached.isEmpty()) {
            throw new BusinessException("Joignez un message ou au moins un fichier");
        }
        if (text.length() > MESSAGE_MAX_LENGTH) {
            throw new BusinessException(
                    "Le message ne doit pas dépasser " + MESSAGE_MAX_LENGTH + " caractères");
        }
        if (attached.size() > MAX_FILES) {
            throw new BusinessException("Maximum " + MAX_FILES + " fichiers par réponse");
        }

        Dossier dossier = findWaitingDossier(accessCode);
        Observation request = lastRequest(dossier);
        Instant deadline = dossier.getAdditionalInfoDeadline();
        boolean late = deadline != null && now.isAfter(deadline);

        // Les fichiers passent par le stockage existant : types, taille et code de suivi y sont
        // contrôlés. Le statut est encore EN_ATTENTE_COMPLEMENT, donc le dépôt est autorisé.
        int uploaded = attached.isEmpty() ? 0
                : attachmentStorageService.upload(dossier.getId().toString(), attached,
                        dossier.getAccessCode(), null, null, null, null).size();

        String content = (late ? "Reçu en retard (échéance du " + DAY.format(deadline) + ") — " : "")
                + (text.isEmpty() ? "Pièces jointes uniquement (" + uploaded + " fichier(s))." : text);

        dossier.setStatus(DossierStatus.EN_ETUDE_OPPORTUNITE);
        dossierRepository.save(dossier);

        auditRecorder.addDeclarantObservation(dossier, ObservationType.COMPLEMENT_RESPONSE,
                content, request.getAuthor(), DECLARANT_LABEL);

        notificationRepository.save(Notification.builder()
                .dossier(dossier)
                .type(NotificationType.INTERNAL_ALERT)
                .channel(NotificationChannel.PORTAL)
                .subject("Complément reçu")
                .content("Le déclarant a répondu à la demande de complément"
                        + (late ? " (en retard)" : "") + ".")
                .scheduledAt(now)
                .build());

        auditRecorder.recordStatusChange(dossier,
                DossierStatus.EN_ATTENTE_COMPLEMENT, DossierStatus.EN_ETUDE_OPPORTUNITE,
                "Complément reçu du déclarant" + (late ? " (en retard)" : ""), null, ipAddress);

        auditService.logAction(null, DECLARANT_LABEL, null, "RECEVOIR_COMPLEMENT", "DOSSIER",
                dossier.getId().toString(),
                "Complément reçu via le portail" + (late ? " (en retard)" : "")
                        + " — " + uploaded + " fichier(s)",
                ipAddress, null);

        return new ComplementSubmissionResponse(
                DossierStatus.EN_ETUDE_OPPORTUNITE.name(), late, uploaded);
    }

    private Dossier findWaitingDossier(String accessCode) {
        Dossier dossier = dossierRepository
                .findByAccessCode(accessCode == null ? "" : accessCode.trim())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Dossier introuvable avec ce code d'accès"));
        if (dossier.getStatus() != DossierStatus.EN_ATTENTE_COMPLEMENT) {
            throw new ConflictException(NOT_WAITING);
        }
        return dossier;
    }

    private Observation lastRequest(Dossier dossier) {
        return observationRepository
                .findTopByDossierIdAndTypeOrderByCreatedAtDesc(
                        dossier.getId(), ObservationType.COMPLEMENT_REQUEST)
                .orElseThrow(() -> new BusinessException(
                        "La demande de complément est introuvable pour ce dossier"));
    }
}
