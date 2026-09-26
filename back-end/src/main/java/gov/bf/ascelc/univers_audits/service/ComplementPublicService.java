package gov.bf.ascelc.univers_audits.service;

import gov.bf.ascelc.univers_audits.enums.DossierStatus;
import gov.bf.ascelc.univers_audits.enums.ObservationType;
import gov.bf.ascelc.univers_audits.model.dto.response.ComplementRequestResponse;
import gov.bf.ascelc.univers_audits.model.entity.Dossier;
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

import java.time.Instant;

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
