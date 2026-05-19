package gov.bf.ascelc.univers_audits.service.impl;

import gov.bf.ascelc.univers_audits.enums.DossierStatus;
import gov.bf.ascelc.univers_audits.enums.NotificationChannel;
import gov.bf.ascelc.univers_audits.enums.NotificationType;
import gov.bf.ascelc.univers_audits.enums.ObservationType;
import gov.bf.ascelc.univers_audits.mapper.DeclarantMapper;
import gov.bf.ascelc.univers_audits.mapper.DossierMapper;
import gov.bf.ascelc.univers_audits.model.dto.request.DossierCreateRequest;
import gov.bf.ascelc.univers_audits.model.dto.request.DossierUpdateRequest;
import gov.bf.ascelc.univers_audits.model.dto.request.StatusTransitionRequest;
import gov.bf.ascelc.univers_audits.model.dto.response.DossierResponse;
import gov.bf.ascelc.univers_audits.model.dto.response.WitnessResponse;
import gov.bf.ascelc.univers_audits.model.entity.*;
import gov.bf.ascelc.univers_audits.repository.*;
import gov.bf.ascelc.univers_audits.service.DossierService;
import gov.bf.ascelc.univers_audits.service.NotificationDispatcherService;
import gov.bf.ascelc.univers_audits.shared.exceptions.BusinessException;
import gov.bf.ascelc.univers_audits.shared.exceptions.ResourceNotFoundException;
import gov.bf.ascelc.univers_audits.shared.utils.AccessCodeGenerator;
import gov.bf.ascelc.univers_audits.shared.utils.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.Year;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DossierServiceImpl implements DossierService {

    private final DossierRepository             dossierRepository;
    private final DeclarantRepository           declarantRepository;
    private final AgentRepository               agentRepository;
    private final StatusHistoryRepository       statusHistoryRepository;
    private final NotificationRepository        notificationRepository;
    private final ObservationRepository         observationRepository;
    private final DossierMapper                 dossierMapper;
    private final DeclarantMapper               declarantMapper;
    private final AccessCodeGenerator           accessCodeGenerator;
    private final SecurityUtils                 securityUtils;
    private final NotificationDispatcherService notificationDispatcher;


    // ══════════════════════════════════════════════════════════════
    //  LECTURE
    // ══════════════════════════════════════════════════════════════

    @Override
    public DossierResponse findById(UUID id) {
        Dossier dossier = getDossierOrThrow(id);
        // Trace chaque accès à un dossier lanceur d'alerte protégé
        logSensitiveAccessIfProtected(dossier, "findById");
        return maskSensitiveData(dossierMapper.toResponse(dossier));
    }

    @Override
    public DossierResponse findByAccessCode(String accessCode) {
        Dossier dossier = dossierRepository
                .findByAccessCode(accessCode)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Dossier introuvable avec ce code d'accès"));

        return maskSensitiveData(dossierMapper.toResponse(dossier));
    }

    @Override
    public Page<DossierResponse> findByStatus(
            DossierStatus status, Pageable pageable) {
        return dossierRepository
                .findByStatus(status, pageable)
                .map(d -> maskSensitiveData(dossierMapper.toResponse(d)));
    }

    @Override
    public Page<DossierResponse> findMyDossiers(Pageable pageable) {
        Agent agent = getCurrentAgent();
        return dossierRepository
                .findByAgentInChargeId(agent.getId(), pageable)
                .map(d -> maskSensitiveData(dossierMapper.toResponse(d)));
    }

    @Override
    public Page<DossierResponse> findAll(Pageable pageable) {
        return dossierRepository.findAll(pageable)
                .map(d -> maskSensitiveData(dossierMapper.toResponse(d)));
    }


    // ══════════════════════════════════════════════════════════════
    //  SOUMISSION
    // ══════════════════════════════════════════════════════════════

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DossierResponse submit(DossierCreateRequest request,
                                  String ipAddress) {
        log.info("Nouvelle soumission — mode: {}", request.getSubmissionMode());


        if (request.getDeclarantData() != null
                && Boolean.TRUE.equals(
                request.getDeclarantData().getProtectionRequested())
                && !Boolean.TRUE.equals(
                request.getDeclarantData().getProtectionAcknowledged())) {
            throw new BusinessException(
                    "Vous devez confirmer avoir pris connaissance des conditions "
                            + "de la protection lanceur d'alerte (Loi N°010-2004/AN) "
                            + "avant de pouvoir la demander.");
        }

        Declarant declarant = resolveDeclarant(request);
        Dossier   dossier   = dossierMapper.toEntity(request);
        dossier.setDeclarant(declarant);
        dossier.setStatus(DossierStatus.SOUMIS);

        if (dossier.getIsConfidential() == null) {
            dossier.setIsConfidential(false);
        }


        boolean protectionRequested = declarant != null
                && Boolean.TRUE.equals(declarant.getProtectionRequested());

        if (protectionRequested) {
            dossier.setIsConfidential(true);
            log.warn("[SECURITE] Protection lanceur d'alerte — dossier marqué "
                    + "confidentiel d'office à la soumission. IP: {}", ipAddress);
        }


        String accessCode;
        do {
            accessCode = accessCodeGenerator.generate();
        } while (dossierRepository.existsByAccessCode(accessCode));
        dossier.setAccessCode(accessCode);

        Dossier saved = dossierRepository.save(dossier);

        notificationDispatcher.dispatchAccessCode(saved);


        if (request.getSubmissionMode() != null
                && (request.getSubmissionMode().name().contains("AUDIO")
                || request.getSubmissionMode()
                == gov.bf.ascelc.univers_audits.enums.SubmissionMode.PHONE)) {
            createNotification(saved,
                    NotificationType.INTERNAL_ALERT,
                    NotificationChannel.PORTAL,
                    "ALERTE — Dénonciation audio à traiter",
                    "Un citoyen a soumis une dénonciation vocale. "
                            + "Veuillez écouter l'enregistrement et constituer "
                            + "le dossier. Code: " + saved.getAccessCode(),
                    Instant.now());
        }


        if (protectionRequested) {
            createNotification(saved,
                    NotificationType.INTERNAL_ALERT,
                    NotificationChannel.PORTAL,
                    "⚠ PROTECTION LANCEUR D'ALERTE — Soumission reçue",
                    "Un déclarant a invoqué la protection lanceur d'alerte "
                            + "(Loi N°010-2004/AN) dès la soumission. "
                            + "Code d'accès : " + saved.getAccessCode()
                            + ". Le dossier a été automatiquement marqué confidentiel.",
                    Instant.now());
        }

        recordStatusChange(saved, null, DossierStatus.SOUMIS,
                "Dossier soumis via " + request.getSubmissionMode(),
                null, ipAddress);

        log.info("Dossier créé — accessCode: {}", accessCode);
        return dossierMapper.toResponse(saved);
    }


    // ══════════════════════════════════════════════════════════════
    //  ENREGISTREMENT (SOUMIS → RECU)
    // ══════════════════════════════════════════════════════════════

    @Override
    @Transactional
    public DossierResponse registerReception(
            UUID dossierId,
            StatusTransitionRequest request,
            String ipAddress) {

        Dossier dossier = getDossierOrThrow(dossierId);
        validateTransition(dossier, DossierStatus.RECU);
        Agent agent = getCurrentAgent();

        String number = generateUniqueNumber();
        dossier.setNumber(number);
        dossier.registerReception(agent);

        addObservation(dossier,
                ObservationType.INTERNAL_NOTE,
                "Dossier enregistré. Numéro attribué : " + number,
                false, agent);

        // ── Alerte protection lanceur d'alerte ──────────────────────────
        if (dossier.getDeclarant() != null
                && Boolean.TRUE.equals(
                dossier.getDeclarant().getProtectionRequested())) {

            // Observation confidentielle — visible CGE / CGEA uniquement
            addObservation(dossier,
                    ObservationType.INTERNAL_NOTE,
                    "⚠ PROTECTION LANCEUR D'ALERTE DEMANDÉE — Loi N°010-2004/AN. "
                            + "Ce déclarant a demandé une protection officielle. "
                            + "Aucune information permettant son identification "
                            + "ne doit être divulguée.",
                    true, agent);

            // Notification portail → CGE / CGEA
            createNotification(dossier,
                    NotificationType.INTERNAL_ALERT,
                    NotificationChannel.PORTAL,
                    "PROTECTION LANCEUR D'ALERTE — Dossier " + number,
                    "Le déclarant du dossier " + number
                            + " a invoqué la protection lanceur d'alerte "
                            + "(Loi N°010-2004/AN). "
                            + "Veuillez prendre les mesures de protection appropriées.",
                    Instant.now());

            log.warn("[SECURITE] PROTECTION LANCEUR D'ALERTE activée — "
                            + "dossier: {}, agent: {}, IP: {}",
                    number, agent.getMatricule(), ipAddress);
        }


        createNotification(dossier,
                NotificationType.RECEIPT_B4,
                NotificationChannel.PORTAL,
                "Récépissé de dépôt — " + number,
                "Votre dossier a été enregistré sous le numéro " + number
                        + ". Code de suivi : " + dossier.getAccessCode(),
                Instant.now());

        createNotification(dossier,
                NotificationType.ACKNOWLEDGMENT_B5,
                resolveNotificationChannel(dossier),
                "Accusé de réception — Dossier " + number,
                "L'ASCE-LC accuse réception de votre dossier " + number
                        + " et vous informera des suites dans les meilleurs délais.",
                dossier.getAcknowledgmentDeadline());

        Dossier saved = dossierRepository.save(dossier);

        notificationDispatcher.dispatchAccessCode(saved);

        recordStatusChange(saved,
                DossierStatus.SOUMIS, DossierStatus.RECU,
                "Enregistrement officiel BRPD", agent, ipAddress);

        log.info("Dossier {} enregistré par {}", number, agent.getMatricule());
        return dossierMapper.toResponse(saved);
    }


    // ══════════════════════════════════════════════════════════════
    //  TRANSITIONS WORKFLOW
    // ══════════════════════════════════════════════════════════════

    @Override
    @Transactional
    public DossierResponse startOpportunityStudy(
            UUID dossierId,
            StatusTransitionRequest request,
            String ipAddress) {

        Dossier dossier = getDossierOrThrow(dossierId);
        validateTransition(dossier, DossierStatus.EN_ETUDE_OPPORTUNITE);
        Agent agent = getCurrentAgent();

        dossier.setStatus(DossierStatus.EN_ETUDE_OPPORTUNITE);

        addObservation(dossier,
                ObservationType.ADMISSIBILITY_ANALYSIS,
                "Étude d'opportunité démarrée. "
                        + (request.getReason() != null ? request.getReason() : ""),
                true, agent);

        Dossier saved = dossierRepository.save(dossier);

        recordStatusChange(saved,
                DossierStatus.RECU, DossierStatus.EN_ETUDE_OPPORTUNITE,
                request.getReason(), agent, ipAddress);

        return dossierMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public DossierResponse requestComplement(
            UUID dossierId,
            StatusTransitionRequest request,
            String ipAddress) {

        Dossier dossier = getDossierOrThrow(dossierId);
        validateTransition(dossier, DossierStatus.EN_ATTENTE_COMPLEMENT);
        Agent agent = getCurrentAgent();

        if (request.getReason() == null || request.getReason().isBlank()) {
            throw new BusinessException(
                    "Le motif de la demande de complément est obligatoire");
        }

        dossier.setStatus(DossierStatus.EN_ATTENTE_COMPLEMENT);

        addObservation(dossier,
                ObservationType.COMPLEMENT_REQUEST,
                "Complément d'information demandé : " + request.getReason(),
                false, agent);

        createNotification(dossier,
                NotificationType.COMPLEMENT_REQUEST,
                resolveNotificationChannel(dossier),
                "Complément requis — Dossier " + dossier.getNumber(),
                "L'ASCE-LC a besoin d'informations supplémentaires. Motif : "
                        + request.getReason(),
                dossier.getAdditionalInfoDeadline());

        Dossier saved = dossierRepository.save(dossier);

        notificationDispatcher.dispatchStatusUpdate(saved,
                "Complément requis",
                "Des informations supplémentaires sont nécessaires. Motif : "
                        + request.getReason());

        recordStatusChange(saved,
                DossierStatus.EN_ETUDE_OPPORTUNITE,
                DossierStatus.EN_ATTENTE_COMPLEMENT,
                request.getReason(), agent, ipAddress);

        return dossierMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public DossierResponse complementReceived(
            UUID dossierId,
            StatusTransitionRequest request,
            String ipAddress) {

        Dossier dossier = getDossierOrThrow(dossierId);
        validateTransition(dossier, DossierStatus.EN_ETUDE_OPPORTUNITE);
        Agent agent = getCurrentAgent();

        dossier.setStatus(DossierStatus.EN_ETUDE_OPPORTUNITE);

        addObservation(dossier,
                ObservationType.INTERNAL_NOTE,
                "Complément reçu — reprise de l'analyse.",
                false, agent);

        Dossier saved = dossierRepository.save(dossier);

        recordStatusChange(saved,
                DossierStatus.EN_ATTENTE_COMPLEMENT,
                DossierStatus.EN_ETUDE_OPPORTUNITE,
                "Complément reçu", agent, ipAddress);

        return dossierMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public DossierResponse submitToCtadp(
            UUID dossierId,
            StatusTransitionRequest request,
            String ipAddress) {

        Dossier dossier = getDossierOrThrow(dossierId);
        validateTransition(dossier, DossierStatus.EN_REVUE_CTADP);
        Agent agent = getCurrentAgent();

        dossier.setStatus(DossierStatus.EN_REVUE_CTADP);

        addObservation(dossier,
                ObservationType.CTADP_OPINION,
                "Dossier soumis à la réunion hebdomadaire du CTADP.",
                true, agent);

        Dossier saved = dossierRepository.save(dossier);

        recordStatusChange(saved,
                DossierStatus.EN_ETUDE_OPPORTUNITE,
                DossierStatus.EN_REVUE_CTADP,
                request.getReason(), agent, ipAddress);

        return dossierMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public DossierResponse declareAdmissible(
            UUID dossierId,
            StatusTransitionRequest request,
            String ipAddress) {

        Dossier dossier = getDossierOrThrow(dossierId);
        validateTransition(dossier, DossierStatus.RECEVABLE);
        Agent agent = getCurrentAgent();

        dossier.setStatus(DossierStatus.RECEVABLE);
        dossier.setEligibilityDecisionDate(Instant.now());

        addObservation(dossier,
                ObservationType.CGE_DECISION,
                "Dossier déclaré RECEVABLE par le CGE. "
                        + (request.getReason() != null ? request.getReason() : ""),
                true, agent);

        createNotification(dossier,
                NotificationType.ACKNOWLEDGMENT_B5,
                resolveNotificationChannel(dossier),
                "Votre dossier est recevable — " + dossier.getNumber(),
                "L'ASCE-LC a déclaré votre dossier recevable "
                        + "et va procéder à une investigation.",
                Instant.now().plusSeconds(3L * 24 * 3600));

        Dossier saved = dossierRepository.save(dossier);

        notificationDispatcher.dispatchStatusUpdate(saved,
                "Dossier recevable",
                "L'ASCE-LC a déclaré votre dossier recevable "
                        + "et va procéder à une investigation.");

        recordStatusChange(saved,
                DossierStatus.EN_REVUE_CTADP, DossierStatus.RECEVABLE,
                request.getReason(), agent, ipAddress);

        return dossierMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public DossierResponse declareInadmissible(
            UUID dossierId,
            StatusTransitionRequest request,
            String ipAddress) {

        Dossier dossier = getDossierOrThrow(dossierId);
        validateTransition(dossier, DossierStatus.IRRECEVABLE);
        Agent agent = getCurrentAgent();

        if (request.getReason() == null || request.getReason().isBlank()) {
            throw new BusinessException(
                    "Le motif d'irrecevabilité est obligatoire");
        }

        dossier.setStatus(DossierStatus.IRRECEVABLE);
        dossier.setEligibilityDecisionDate(Instant.now());

        addObservation(dossier,
                ObservationType.CGE_DECISION,
                "Dossier déclaré IRRECEVABLE. Motif : " + request.getReason(),
                true, agent);

        createNotification(dossier,
                NotificationType.INADMISSIBILITY_DECISION,
                resolveNotificationChannel(dossier),
                "Décision sur votre dossier " + dossier.getNumber(),
                "L'ASCE-LC ne peut pas donner suite à votre dossier. Motif : "
                        + request.getReason(),
                Instant.now().plusSeconds(3L * 24 * 3600));

        Dossier saved = dossierRepository.save(dossier);

        notificationDispatcher.dispatchStatusUpdate(saved,
                "Dossier irrecevable",
                "L'ASCE-LC ne peut pas donner suite. Motif : "
                        + request.getReason());

        recordStatusChange(saved,
                DossierStatus.EN_REVUE_CTADP, DossierStatus.IRRECEVABLE,
                request.getReason(), agent, ipAddress);

        return dossierMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public DossierResponse transfer(
            UUID dossierId,
            StatusTransitionRequest request,
            String ipAddress) {

        Dossier dossier = getDossierOrThrow(dossierId);
        validateTransition(dossier, DossierStatus.TRANSFERE);
        Agent agent = getCurrentAgent();

        if (request.getTransferInstitution() == null
                || request.getTransferInstitution().isBlank()) {
            throw new BusinessException(
                    "L'institution destinataire est obligatoire pour un transfert");
        }

        dossier.setStatus(DossierStatus.TRANSFERE);
        dossier.setTransferDate(Instant.now());
        dossier.setTransferInstitution(request.getTransferInstitution());
        dossier.setEligibilityDecisionDate(Instant.now());

        addObservation(dossier,
                ObservationType.TRANSFER_NOTE,
                "Dossier transféré à : " + request.getTransferInstitution()
                        + ". Motif : " + request.getReason(),
                false, agent);

        createNotification(dossier,
                NotificationType.TRANSFER_DECISION,
                resolveNotificationChannel(dossier),
                "Transfert de votre dossier " + dossier.getNumber(),
                "Votre dossier a été transmis à "
                        + request.getTransferInstitution()
                        + " qui est compétente pour le traiter.",
                Instant.now().plusSeconds(7L * 24 * 3600));

        Dossier saved = dossierRepository.save(dossier);

        notificationDispatcher.dispatchStatusUpdate(saved,
                "Dossier transféré",
                "Votre dossier a été transmis à "
                        + request.getTransferInstitution());

        recordStatusChange(saved,
                DossierStatus.EN_REVUE_CTADP, DossierStatus.TRANSFERE,
                request.getReason(), agent, ipAddress);

        return dossierMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public DossierResponse close(
            UUID dossierId,
            StatusTransitionRequest request,
            String ipAddress) {

        Dossier dossier = getDossierOrThrow(dossierId);
        Agent agent = getCurrentAgent();

        DossierStatus previousStatus = dossier.getStatus();
        DossierStatus newStatus =
                (previousStatus == DossierStatus.DECISION_RENDUE)
                        ? DossierStatus.CLOS
                        : DossierStatus.CLASSE;

        dossier.setStatus(newStatus);
        dossier.setClosingDate(Instant.now());

        addObservation(dossier,
                ObservationType.INTERNAL_NOTE,
                "Dossier clôturé. "
                        + (request.getReason() != null ? request.getReason() : ""),
                false, agent);

        Dossier saved = dossierRepository.save(dossier);

        notificationDispatcher.dispatchStatusUpdate(saved,
                newStatus == DossierStatus.CLOS
                        ? "Dossier clôturé" : "Dossier classé",
                "Votre dossier a été traité et officiellement clôturé. "
                        + "Merci pour votre contribution à la lutte contre la corruption.");

        recordStatusChange(saved, previousStatus, newStatus,
                request.getReason(), agent, ipAddress);

        return dossierMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public DossierResponse update(UUID dossierId,
                                  DossierUpdateRequest request) {
        Dossier dossier = getDossierOrThrow(dossierId);

        if (dossier.isClosed()) {
            throw new BusinessException(
                    "Un dossier clôturé ne peut plus être modifié");
        }

        // Seuls CGE et CGEA peuvent modifier un dossier lanceur d'alerte protégé
        boolean isProtected = dossier.getDeclarant() != null
                && Boolean.TRUE.equals(
                dossier.getDeclarant().getProtectionRequested());
        boolean isCge  = securityUtils.hasRole("CGE");
        boolean isCgea = securityUtils.hasRole("CGEA");

        if (isProtected && !isCge && !isCgea) {
            throw new BusinessException(
                    "Modification restreinte — ce dossier concerne un lanceur "
                            + "d'alerte sous protection (Loi N°010-2004/AN). "
                            + "Seuls le CGE et le CGEA sont habilités à le modifier.");
        }

        dossierMapper.updateEntity(request, dossier);

        if (dossier.getIsConfidential() == null) {
            dossier.setIsConfidential(false);
        }

        return dossierMapper.toResponse(dossierRepository.save(dossier));
    }

    @Override
    @Transactional
    public DossierResponse setConfidential(UUID dossierId,
                                           boolean value,
                                           StatusTransitionRequest request) {
        Dossier dossier = getDossierOrThrow(dossierId);
        Agent agent = getCurrentAgent();

        boolean isProtected = dossier.getDeclarant() != null
                && Boolean.TRUE.equals(
                dossier.getDeclarant().getProtectionRequested());
        boolean isCge  = securityUtils.hasRole("CGE");
        boolean isCgea = securityUtils.hasRole("CGEA");

        // ADMIN_DDIC ne peut pas retirer la confidentialité
        // d'un dossier lanceur d'alerte protégé
        if (isProtected && !value && !isCge && !isCgea) {
            throw new BusinessException(
                    "Impossible de retirer la confidentialité d'un dossier "
                            + "lanceur d'alerte protégé (Loi N°010-2004/AN). "
                            + "Cette action est réservée au CGE et au CGEA.");
        }

        dossier.setIsConfidential(value);

        addObservation(dossier,
                ObservationType.INTERNAL_NOTE,
                "Dossier marqué comme "
                        + (value ? "CONFIDENTIEL" : "NON CONFIDENTIEL")
                        + (request.getReason() != null
                        ? " — Motif : " + request.getReason() : ""),
                true, agent);

        Dossier saved = dossierRepository.save(dossier);

        log.info("Dossier {} — confidentiel: {}", saved.getNumber(), value);
        return dossierMapper.toResponse(saved);
    }



    @Override
    @Transactional
    public DossierResponse revokeWhistleblowerProtection(
            UUID dossierId,
            StatusTransitionRequest request) {

        Dossier dossier = getDossierOrThrow(dossierId);
        Agent agent = getCurrentAgent();

        if (!securityUtils.hasRole("CGE") && !securityUtils.hasRole("CGEA")) {
            throw new BusinessException(
                    "Seuls le CGE et le CGEA peuvent révoquer "
                            + "une protection lanceur d'alerte.");
        }

        if (request.getReason() == null || request.getReason().isBlank()) {
            throw new BusinessException(
                    "Le motif de révocation est obligatoire.");
        }

        if (dossier.getDeclarant() == null
                || !Boolean.TRUE.equals(
                dossier.getDeclarant().getProtectionRequested())) {
            throw new BusinessException(
                    "Ce dossier ne bénéficie d'aucune protection "
                            + "lanceur d'alerte active.");
        }

        dossier.getDeclarant().setProtectionRequested(false);
        declarantRepository.save(dossier.getDeclarant());

        addObservation(dossier,
                ObservationType.CGE_DECISION,
                "⚠ Protection lanceur d'alerte RÉVOQUÉE par décision CGE/CGEA. "
                        + "Motif : " + request.getReason(),
                true, agent);

        log.warn("[SECURITE] Protection lanceur d'alerte RÉVOQUÉE — "
                        + "dossier: {}, par: {}, motif: {}",
                dossier.getNumber(), agent.getMatricule(), request.getReason());

        return maskSensitiveData(
                dossierMapper.toResponse(dossierRepository.save(dossier)));
    }


    // ══════════════════════════════════════════════════════════════
    //  MÉTHODES PRIVÉES — UTILITAIRES
    // ══════════════════════════════════════════════════════════════

    private Dossier getDossierOrThrow(UUID id) {
        return dossierRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Dossier introuvable : " + id));
    }

    private Agent getCurrentAgent() {
        String keycloakId = securityUtils.getCurrentKeycloakId()
                .orElseThrow(() -> new BusinessException("Agent non authentifié"));
        return agentRepository.findByKeycloakId(keycloakId)
                .orElseThrow(() -> new BusinessException(
                        "Agent introuvable. Contactez l'administrateur DDIC."));
    }

    private void validateTransition(Dossier dossier, DossierStatus target) {
        if (dossier.isClosed()) {
            throw new BusinessException(
                    "Ce dossier est clôturé — aucune action possible");
        }

        boolean allowed = switch (target) {
            case RECU ->
                    dossier.getStatus() == DossierStatus.SOUMIS;
            case EN_ETUDE_OPPORTUNITE ->
                    dossier.getStatus() == DossierStatus.RECU
                            || dossier.getStatus() == DossierStatus.EN_ATTENTE_COMPLEMENT;
            case EN_ATTENTE_COMPLEMENT ->
                    dossier.getStatus() == DossierStatus.EN_ETUDE_OPPORTUNITE;
            case EN_REVUE_CTADP ->
                    dossier.getStatus() == DossierStatus.EN_ETUDE_OPPORTUNITE;
            case RECEVABLE, IRRECEVABLE, TRANSFERE ->
                    dossier.getStatus() == DossierStatus.EN_REVUE_CTADP;
            case EN_INVESTIGATION ->
                    dossier.getStatus() == DossierStatus.RECEVABLE;
            case RAPPORT_PRODUIT ->
                    dossier.getStatus() == DossierStatus.EN_INVESTIGATION;
            case DECISION_RENDUE ->
                    dossier.getStatus() == DossierStatus.RAPPORT_PRODUIT;
            case CLOS ->
                    dossier.getStatus() == DossierStatus.DECISION_RENDUE;
            case CLASSE ->
                    dossier.getStatus() == DossierStatus.IRRECEVABLE
                            || dossier.getStatus() == DossierStatus.TRANSFERE;
            default -> false;
        };

        if (!allowed) {
            throw new BusinessException(
                    "Transition invalide : "
                            + dossier.getStatus() + " → " + target);
        }
    }

    private Declarant resolveDeclarant(DossierCreateRequest request) {
        if (request.getDeclarantId() != null) {
            return declarantRepository.findById(request.getDeclarantId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Déclarant introuvable : " + request.getDeclarantId()));
        }

        if (request.getDeclarantData() != null) {
            String email = request.getDeclarantData().getEmail();
            String phone = request.getDeclarantData().getPhoneNumber();
            if (email != null || phone != null) {
                var existing = declarantRepository.findByEmailOrPhone(email, phone);
                if (!existing.isEmpty()) return existing.get(0);
            }
            Declarant declarant = declarantMapper.toEntity(request.getDeclarantData());
            return declarantRepository.save(declarant);
        }

        return null;
    }

    private String generateUniqueNumber() {
        int year   = Year.now().getValue();
        long count = dossierRepository.countByReceptionDateBetween(
                Instant.parse(year + "-01-01T00:00:00Z"),
                Instant.now()) + 1;
        String number;
        do {
            number = accessCodeGenerator.generateDossierNumber((int) count, year);
            count++;
        } while (dossierRepository.existsByNumber(number));
        return number;
    }

    private void addObservation(Dossier dossier, ObservationType type,
                                String content, boolean confidential,
                                Agent agent) {
        Observation obs = Observation.builder()
                .dossier(dossier)
                .type(type)
                .content(content)
                .confidential(confidential)
                .author(agent)
                .authorFullName(agent.getNomComplet())
                .statusSnapshot(dossier.getStatus())
                .build();
        observationRepository.save(obs);
    }

    private void createNotification(Dossier dossier, NotificationType type,
                                    NotificationChannel channel, String subject,
                                    String content, Instant scheduledAt) {
        Notification notif = Notification.builder()
                .dossier(dossier)
                .type(type)
                .channel(channel)
                .subject(subject)
                .content(content)
                .scheduledAt(scheduledAt)
                .build();
        notificationRepository.save(notif);
    }

    private void recordStatusChange(Dossier dossier, DossierStatus previous,
                                    DossierStatus next, String reason,
                                    Agent agent, String ipAddress) {
        StatusHistory history = StatusHistory.builder()
                .dossier(dossier)
                .previousStatus(previous)
                .newStatus(next)
                .reason(reason)
                .agent(agent)
                .agentFullName(agent != null ? agent.getNomComplet() : "Système")
                .ipAddress(ipAddress)
                .build();
        statusHistoryRepository.save(history);
    }

    private NotificationChannel resolveNotificationChannel(Dossier dossier) {
        if (dossier.getDeclarant() == null)
            return NotificationChannel.PORTAL;
        if (dossier.getDeclarant().getEmail() != null)
            return NotificationChannel.EMAIL;
        if (dossier.getDeclarant().getPhoneNumber() != null)
            return NotificationChannel.SMS;
        return NotificationChannel.PORTAL;
    }


    private void logSensitiveAccessIfProtected(Dossier dossier, String method) {
        if (dossier.getDeclarant() == null
                || !Boolean.TRUE.equals(
                dossier.getDeclarant().getProtectionRequested())) {
            return;
        }
        String callerInfo = securityUtils.getCurrentKeycloakId()
                .orElse("ANONYMOUS");
        log.warn("[AUDIT-PROTECTION] Accès dossier lanceur d'alerte — "
                        + "dossier: {}, méthode: {}, appelant: {}, horodatage: {}",
                dossier.getNumber() != null ? dossier.getNumber() : dossier.getId(),
                method, callerInfo, Instant.now());
    }




    private DossierResponse maskSensitiveData(DossierResponse response) {

        boolean isCge   = securityUtils.hasRole("CGE");
        boolean isCgea  = securityUtils.hasRole("CGEA");
        boolean isAdmin = securityUtils.hasRole("ADMIN_DDIC");

        boolean canSeeConfidential = isCge || isCgea || isAdmin;


        if (Boolean.TRUE.equals(response.getIsConfidential())
                && !canSeeConfidential) {
            response.setDeclarant(null);
            response.setDescription("** Contenu confidentiel — accès restreint **");
            response.setMotifs(null);
            response.setIncidentLocation(null);
            response.setEstimatedLoss(null);
            response.setObservations(null);
            response.setWitnesses(null);
            response.setTargetedParties(null);
            log.debug("Dossier {} — accès restreint (confidentiel)",
                    response.getNumber());
        }


        if (response.getDeclarant() != null
                && Boolean.TRUE.equals(
                response.getDeclarant().getProtectionRequested())
                && !isCge && !isCgea) {
            response.getDeclarant().setFirstName(null);
            response.getDeclarant().setLastName(null);
            response.getDeclarant().setEmail(null);
            response.getDeclarant().setPhoneNumber(null);
            response.getDeclarant().setAddress(null);
            response.getDeclarant().setCommune(null);
            response.getDeclarant().setProvince(null);
            response.getDeclarant().setProfession(null);
            response.getDeclarant().setDisplayName(
                    "Lanceur d'alerte protégé (Loi N°010-2004/AN)");
            log.debug("Dossier {} — identité lanceur d'alerte masquée",
                    response.getNumber());
        }

        if (response.getDeclarant() != null
                && Boolean.TRUE.equals(response.getDeclarant().getAnonymous())) {
            response.getDeclarant().setFirstName(null);
            response.getDeclarant().setLastName(null);
            response.getDeclarant().setEmail(null);
            response.getDeclarant().setPhoneNumber(null);
            response.getDeclarant().setAddress(null);
            response.getDeclarant().setCommune(null);
            response.getDeclarant().setProvince(null);
        }

        if (response.getWitnesses() != null) {
            for (WitnessResponse witness : response.getWitnesses()) {
                if (Boolean.TRUE.equals(witness.getAnonymous())) {
                    witness.setFirstName(null);
                    witness.setLastName(null);
                    witness.setEmail(null);
                    witness.setPhoneNumber(null);
                    witness.setAddress(null);
                    witness.setProfession(null);
                }
            }
        }

        return response;
    }
}