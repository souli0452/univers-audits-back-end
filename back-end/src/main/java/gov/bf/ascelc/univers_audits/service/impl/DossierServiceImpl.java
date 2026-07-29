package gov.bf.ascelc.univers_audits.service.impl;

import gov.bf.ascelc.univers_audits.enums.*;
import gov.bf.ascelc.univers_audits.mapper.DeclarantMapper;
import gov.bf.ascelc.univers_audits.mapper.DossierDetailsMapper;
import gov.bf.ascelc.univers_audits.mapper.DossierMapper;
import gov.bf.ascelc.univers_audits.model.dto.request.DossierCreateRequest;
import gov.bf.ascelc.univers_audits.model.dto.request.DossierUpdateRequest;
import gov.bf.ascelc.univers_audits.model.dto.request.SetPriorityRequest;
import gov.bf.ascelc.univers_audits.model.dto.request.StatusTransitionRequest;
import gov.bf.ascelc.univers_audits.model.dto.response.DossierResponse;
import gov.bf.ascelc.univers_audits.model.dto.response.WitnessResponse;
import gov.bf.ascelc.univers_audits.model.entity.*;
import gov.bf.ascelc.univers_audits.repository.*;
import gov.bf.ascelc.univers_audits.service.DossierService;
import gov.bf.ascelc.univers_audits.service.NotificationDispatcherService;
import gov.bf.ascelc.univers_audits.service.ParametreDelaiService;
import gov.bf.ascelc.univers_audits.shared.exceptions.BusinessException;
import gov.bf.ascelc.univers_audits.shared.exceptions.ConflictException;
import gov.bf.ascelc.univers_audits.shared.exceptions.ResourceNotFoundException;
import gov.bf.ascelc.univers_audits.shared.utils.AccessCodeGenerator;
import gov.bf.ascelc.univers_audits.shared.utils.AgentContextResolver;
import gov.bf.ascelc.univers_audits.shared.utils.DossierAuditRecorder;
import gov.bf.ascelc.univers_audits.shared.utils.NatureSaisineResolver;
import gov.bf.ascelc.univers_audits.shared.utils.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.Year;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DossierServiceImpl implements DossierService {

    private final DossierRepository             dossierRepository;
    private final DeclarantRepository           declarantRepository;
    private final NotificationRepository        notificationRepository;
    private final ObservationRepository         observationRepository;
    private final DossierMapper                 dossierMapper;
    private final DossierDetailsMapper           dossierDetailsMapper;
    private final DeclarantMapper               declarantMapper;
    private final AccessCodeGenerator           accessCodeGenerator;
    private final SecurityUtils                 securityUtils;
    private final NotificationDispatcherService notificationDispatcher;
    private final AgentContextResolver          agentContextResolver;
    private final DossierAuditRecorder          auditRecorder;
    private final ParametreDelaiService          parametreDelaiService;
    private final NatureSaisineResolver         natureSaisineResolver;


    // ════════════════════════════════════════════════════════════
    //  LECTURE
    // ════════════════════════════════════════════════════════════

    @Override
    public DossierResponse findById(UUID id) {
        Dossier dossier = getDossierOrThrow(id);
        logSensitiveAccessIfProtected(dossier, "findById");

        boolean isAdmin = securityUtils.hasRole("ADMIN_DDIC");
        boolean isCge   = securityUtils.hasRole("CGE");
        boolean isCgea  = securityUtils.hasRole("CGEA");

        if (!isAdmin && !isCge && !isCgea) {
            Agent agent = agentContextResolver.getCurrentAgent();
            boolean isAssigned = dossier.getAgentInCharge() != null
                    && dossier.getAgentInCharge().getId().equals(agent.getId());
            if (!isAssigned) {
                throw new BusinessException(
                        "Accès refusé — ce dossier ne vous est pas assigné");
            }
        }

        return enrichAndMaskDetail(dossier);
    }

    @Override
    public DossierResponse findByAccessCode(String accessCode) {
        Dossier dossier = dossierRepository
                .findByAccessCode(accessCode)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Dossier introuvable avec ce code d'accès"));
        return enrichAndMaskDetail(dossier);
    }

    @Override
    public Page<DossierResponse> findByStatus(
            DossierStatus status, Pageable pageable) {
        return dossierRepository
                .findByStatus(status, pageable)
                .map(this::enrichAndMask);
    }

    @Override
    public Page<DossierResponse> findMyDossiers(Pageable pageable) {
        Agent agent = agentContextResolver.getCurrentAgent();
        return dossierRepository
                .findByAgentInChargeId(agent.getId(), pageable)
                .map(this::enrichAndMask);
    }

    @Override
    public Page<DossierResponse> findAll(Pageable pageable) {
        boolean isAdmin = securityUtils.hasRole("ADMIN_DDIC");
        boolean isCge   = securityUtils.hasRole("CGE");
        boolean isCgea  = securityUtils.hasRole("CGEA");

        if (isAdmin || isCge || isCgea) {
            return dossierRepository.findAll(pageable)
                    .map(this::enrichAndMask);
        }

        Agent agent = agentContextResolver.getCurrentAgent();
        log.debug("[Dossiers] Accès restreint — agent: {} voit uniquement ses dossiers",
                agent.getMatricule());
        return dossierRepository
                .findByAgentInChargeId(agent.getId(), pageable)
                .map(this::enrichAndMask);
    }

    @Override
    public Page<DossierResponse> findByReceptionDateBetween(
            Instant start, Instant end, Pageable pageable) {
        return dossierRepository
                .findByReceptionDateBetween(start, end, pageable)
                .map(this::enrichAndMask);
    }


    // ════════════════════════════════════════════════════════════
    //  SOUMISSION
    // ════════════════════════════════════════════════════════════

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
        if (declarant == null) {
            throw new BusinessException(
                    "Le déclarant est requis pour déterminer la nature de la saisine.");
        }

        TypeSaisine natureSaisine = natureSaisineResolver.resolve(
                declarant.getTypeDeclarant(), request.getQuality(),
                declarant.isAnonymous());

        Dossier dossier = dossierMapper.toEntity(request);
        dossier.setDeclarant(declarant);
        dossier.setType(natureSaisine);
        if (natureSaisine == TypeSaisine.SIGNALEMENT
                || natureSaisine == TypeSaisine.AUTO_SAISINE) {
            // La qualité (victime/représentant/témoin) n'a de sens que pour
            // une plainte ou une dénonciation ; le mapper l'ayant recopiée
            // depuis la requête, on la neutralise ici pour ces deux natures.
            dossier.setQuality(null);
        }
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
                            + "le dossier.",
                    Instant.now());
        }

        if (protectionRequested) {
            createNotification(saved,
                    NotificationType.INTERNAL_ALERT,
                    NotificationChannel.PORTAL,
                    "⚠ PROTECTION LANCEUR D'ALERTE — Soumission reçue",
                    "Un déclarant a invoqué la protection lanceur d'alerte "
                            + "(Loi N°010-2004/AN) dès la soumission. "
                            + "Le dossier a été automatiquement marqué confidentiel.",
                    Instant.now());
        }

        auditRecorder.recordStatusChange(saved, null, DossierStatus.SOUMIS,
                "Dossier soumis via " + request.getSubmissionMode(),
                null, ipAddress);

        log.info("Dossier créé — accessCode: {}", accessCode);
        return dossierMapper.toResponse(saved);
    }


    // ════════════════════════════════════════════════════════════
    //  TRANSITIONS DE STATUT
    // ════════════════════════════════════════════════════════════

    @Override
    @Transactional
    public DossierResponse registerReception(
            UUID dossierId,
            StatusTransitionRequest request,
            String ipAddress) {

        Dossier dossier = getDossierOrThrow(dossierId);
        validateTransition(dossier, DossierStatus.RECU);
        Agent agent = agentContextResolver.getCurrentAgent();

        String number = generateUniqueNumber();
        dossier.setNumber(number);
        int accuseReceptionJours = parametreDelaiService
                .resolveDelaiJours("ACCUSE_RECEPTION");
        int demandeComplementJours = parametreDelaiService
                .resolveDelaiJours("DEMANDE_COMPLEMENT");
        dossier.registerReception(agent, accuseReceptionJours, demandeComplementJours);

        auditRecorder.addObservation(dossier,
                ObservationType.INTERNAL_NOTE,
                "Dossier enregistré. Numéro attribué : " + number,
                false, agent);

        if (dossier.getDeclarant() != null
                && Boolean.TRUE.equals(
                dossier.getDeclarant().getProtectionRequested())) {

            auditRecorder.addObservation(dossier,
                    ObservationType.INTERNAL_NOTE,
                    "⚠ PROTECTION LANCEUR D'ALERTE DEMANDÉE — Loi N°010-2004/AN. "
                            + "Ce déclarant a demandé une protection officielle. "
                            + "Aucune information permettant son identification "
                            + "ne doit être divulguée.",
                    true, agent);

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
                "Votre dossier a été enregistré. Le code de suivi vous a été "
                        + "communiqué séparément lors de votre soumission.",
                Instant.now());

        createNotification(dossier,
                NotificationType.ACKNOWLEDGMENT_B5,
                resolveNotificationChannel(dossier),
                "Accusé de réception — votre dossier",
                "L'ASCE-LC accuse réception de votre dossier "
                        + "et vous informera des suites dans les meilleurs délais.",
                dossier.getAcknowledgmentDeadline());

        Dossier saved = dossierRepository.save(dossier);

        notificationDispatcher.dispatchAccessCode(saved);

        auditRecorder.recordStatusChange(saved,
                DossierStatus.SOUMIS, DossierStatus.RECU,
                "Enregistrement officiel BRPD", agent, ipAddress);

        log.info("Dossier {} enregistré par {}", number, agent.getMatricule());
        return enrichAndMaskDetail(saved);
    }

    @Override
    @Transactional
    public DossierResponse startOpportunityStudy(
            UUID dossierId,
            StatusTransitionRequest request,
            String ipAddress) {

        Dossier dossier = getDossierOrThrow(dossierId);
        validateTransition(dossier, DossierStatus.EN_ETUDE_OPPORTUNITE);
        Agent agent = agentContextResolver.getCurrentAgent();

        dossier.setStatus(DossierStatus.EN_ETUDE_OPPORTUNITE);

        auditRecorder.addObservation(dossier,
                ObservationType.ADMISSIBILITY_ANALYSIS,
                "Étude d'opportunité démarrée. "
                        + (request.getReason() != null ? request.getReason() : ""),
                true, agent);

        Dossier saved = dossierRepository.save(dossier);

        auditRecorder.recordStatusChange(saved,
                DossierStatus.RECU, DossierStatus.EN_ETUDE_OPPORTUNITE,
                request.getReason(), agent, ipAddress);

        return enrichAndMaskDetail(saved);
    }

    @Override
    @Transactional
    public DossierResponse requestComplement(
            UUID dossierId,
            StatusTransitionRequest request,
            String ipAddress) {

        Dossier dossier = getDossierOrThrow(dossierId);
        validateTransition(dossier, DossierStatus.EN_ATTENTE_COMPLEMENT);
        Agent agent = agentContextResolver.getCurrentAgent();

        if (request.getReason() == null || request.getReason().isBlank()) {
            throw new BusinessException(
                    "Le motif de la demande de complément est obligatoire");
        }

        dossier.setStatus(DossierStatus.EN_ATTENTE_COMPLEMENT);

        auditRecorder.addObservation(dossier,
                ObservationType.COMPLEMENT_REQUEST,
                request.getReason(),
                false, agent);

        createNotification(dossier,
                NotificationType.COMPLEMENT_REQUEST,
                resolveNotificationChannel(dossier),
                "Information complémentaire requise — votre dossier",
                "L'ASCE-LC a besoin d'informations supplémentaires. Motif : "
                        + request.getReason(),
                dossier.getAdditionalInfoDeadline());

        Dossier saved = dossierRepository.save(dossier);

        notificationDispatcher.dispatchComplementRequest(saved, request.getReason());

        auditRecorder.recordStatusChange(saved,
                DossierStatus.EN_ETUDE_OPPORTUNITE,
                DossierStatus.EN_ATTENTE_COMPLEMENT,
                request.getReason(), agent, ipAddress);

        return enrichAndMaskDetail(saved);
    }

    @Override
    @Transactional
    public DossierResponse complementReceived(
            UUID dossierId,
            StatusTransitionRequest request,
            String ipAddress) {

        Dossier dossier = getDossierOrThrow(dossierId);
        validateTransition(dossier, DossierStatus.EN_ETUDE_OPPORTUNITE);
        Agent agent = agentContextResolver.getCurrentAgent();

        dossier.setStatus(DossierStatus.EN_ETUDE_OPPORTUNITE);

        auditRecorder.addObservation(dossier,
                ObservationType.INTERNAL_NOTE,
                "Complément reçu — reprise de l'analyse. "
                        + (request.getReason() != null ? request.getReason() : ""),
                false, agent);

        Dossier saved = dossierRepository.save(dossier);

        notificationDispatcher.dispatchStatusUpdate(saved, "EN_ETUDE_OPPORTUNITE", null);

        auditRecorder.recordStatusChange(saved,
                DossierStatus.EN_ATTENTE_COMPLEMENT,
                DossierStatus.EN_ETUDE_OPPORTUNITE,
                "Complément reçu", agent, ipAddress);

        return enrichAndMaskDetail(saved);
    }

    @Override
    @Transactional
    public DossierResponse submitToCtadp(
            UUID dossierId,
            StatusTransitionRequest request,
            String ipAddress) {

        Dossier dossier = getDossierOrThrow(dossierId);
        validateTransition(dossier, DossierStatus.EN_REVUE_CTADP);
        Agent agent = agentContextResolver.getCurrentAgent();

        dossier.setStatus(DossierStatus.EN_REVUE_CTADP);

        auditRecorder.addObservation(dossier,
                ObservationType.CTADP_OPINION,
                "Dossier soumis à la réunion hebdomadaire du CTADP.",
                true, agent);

        Dossier saved = dossierRepository.save(dossier);

        auditRecorder.recordStatusChange(saved,
                DossierStatus.EN_ETUDE_OPPORTUNITE,
                DossierStatus.EN_REVUE_CTADP,
                request.getReason(), agent, ipAddress);

        return enrichAndMaskDetail(saved);
    }

    @Override
    @Transactional
    public DossierResponse declareAdmissible(
            UUID dossierId,
            StatusTransitionRequest request,
            String ipAddress) {

        Dossier dossier = getDossierOrThrow(dossierId);
        validateTransition(dossier, DossierStatus.RECEVABLE);
        Agent agent = agentContextResolver.getCurrentAgent();

        dossier.setStatus(DossierStatus.RECEVABLE);
        dossier.setEligibilityDecisionDate(Instant.now());

        auditRecorder.addObservation(dossier,
                ObservationType.CGE_DECISION,
                "Dossier déclaré RECEVABLE par le CGE. "
                        + (request.getReason() != null ? request.getReason() : ""),
                true, agent);

        Dossier saved = dossierRepository.save(dossier);

        notificationDispatcher.dispatchStatusUpdate(saved, "RECEVABLE", request.getReason());

        auditRecorder.recordStatusChange(saved,
                DossierStatus.EN_REVUE_CTADP, DossierStatus.RECEVABLE,
                request.getReason(), agent, ipAddress);

        return enrichAndMaskDetail(saved);
    }

    @Override
    @Transactional
    public DossierResponse declareInadmissible(
            UUID dossierId,
            StatusTransitionRequest request,
            String ipAddress) {

        Dossier dossier = getDossierOrThrow(dossierId);
        validateTransition(dossier, DossierStatus.IRRECEVABLE);
        Agent agent = agentContextResolver.getCurrentAgent();

        if (request.getReason() == null || request.getReason().isBlank()) {
            throw new BusinessException(
                    "Le motif d'irrecevabilité est obligatoire");
        }

        dossier.setStatus(DossierStatus.IRRECEVABLE);
        dossier.setEligibilityDecisionDate(Instant.now());

        auditRecorder.addObservation(dossier,
                ObservationType.CGE_DECISION,
                "Dossier déclaré IRRECEVABLE. Motif : " + request.getReason(),
                true, agent);

        Dossier saved = dossierRepository.save(dossier);

        notificationDispatcher.dispatchStatusUpdate(saved, "IRRECEVABLE", request.getReason());

        auditRecorder.recordStatusChange(saved,
                DossierStatus.EN_REVUE_CTADP, DossierStatus.IRRECEVABLE,
                request.getReason(), agent, ipAddress);

        return enrichAndMaskDetail(saved);
    }

    @Override
    @Transactional
    public DossierResponse transfer(
            UUID dossierId,
            StatusTransitionRequest request,
            String ipAddress) {

        Dossier dossier = getDossierOrThrow(dossierId);
        validateTransition(dossier, DossierStatus.TRANSFERE);
        Agent agent = agentContextResolver.getCurrentAgent();

        if (request.getTransferInstitution() == null
                || request.getTransferInstitution().isBlank()) {
            throw new BusinessException(
                    "L'institution destinataire est obligatoire pour un transfert");
        }

        dossier.setStatus(DossierStatus.TRANSFERE);
        dossier.setTransferDate(Instant.now());
        dossier.setTransferInstitution(request.getTransferInstitution());
        dossier.setEligibilityDecisionDate(Instant.now());

        auditRecorder.addObservation(dossier,
                ObservationType.TRANSFER_NOTE,
                "Dossier transféré à : " + request.getTransferInstitution()
                        + ". Motif : " + request.getReason(),
                false, agent);

        Dossier saved = dossierRepository.save(dossier);

        notificationDispatcher.dispatchTransferExternal(saved, request.getTransferInstitution());

        auditRecorder.recordStatusChange(saved,
                DossierStatus.EN_REVUE_CTADP, DossierStatus.TRANSFERE,
                request.getReason(), agent, ipAddress);

        return enrichAndMaskDetail(saved);
    }

    @Override
    @Transactional
    public DossierResponse close(
            UUID dossierId,
            StatusTransitionRequest request,
            String ipAddress) {

        Dossier dossier = getDossierOrThrow(dossierId);
        Agent agent = agentContextResolver.getCurrentAgent();

        DossierStatus previousStatus = dossier.getStatus();
        DossierStatus newStatus =
                (previousStatus == DossierStatus.DECISION_RENDUE)
                        ? DossierStatus.CLOS
                        : DossierStatus.CLASSE;

        dossier.setStatus(newStatus);
        dossier.setClosingDate(Instant.now());

        auditRecorder.addObservation(dossier,
                ObservationType.INTERNAL_NOTE,
                "Dossier clôturé. "
                        + (request.getReason() != null ? request.getReason() : ""),
                false, agent);

        Dossier saved = dossierRepository.save(dossier);

        notificationDispatcher.dispatchStatusUpdate(saved, newStatus.name(), null);

        auditRecorder.recordStatusChange(saved, previousStatus, newStatus,
                request.getReason(), agent, ipAddress);

        return enrichAndMaskDetail(saved);
    }


    // ════════════════════════════════════════════════════════════
    //  MODIFICATION
    // ════════════════════════════════════════════════════════════

    @Override
    @Transactional
    public DossierResponse update(UUID dossierId, DossierUpdateRequest request) {
        Dossier dossier = getDossierOrThrow(dossierId);

        if (dossier.isClosed()) {
            throw new BusinessException(
                    "Un dossier clôturé ne peut plus être modifié");
        }

        boolean isProtected = dossier.getDeclarant() != null
                && Boolean.TRUE.equals(dossier.getDeclarant().getProtectionRequested());
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

        return enrichAndMaskDetail(dossierRepository.save(dossier));
    }

    @Override
    @Transactional
    public DossierResponse setConfidential(UUID dossierId,
                                           boolean value,
                                           StatusTransitionRequest request) {
        Dossier dossier = getDossierOrThrow(dossierId);
        Agent agent = agentContextResolver.getCurrentAgent();

        boolean isProtected = dossier.getDeclarant() != null
                && Boolean.TRUE.equals(dossier.getDeclarant().getProtectionRequested());
        boolean isCge  = securityUtils.hasRole("CGE");
        boolean isCgea = securityUtils.hasRole("CGEA");

        if (isProtected && !value && !isCge && !isCgea) {
            throw new BusinessException(
                    "Impossible de retirer la confidentialité d'un dossier "
                            + "lanceur d'alerte protégé (Loi N°010-2004/AN). "
                            + "Cette action est réservée au CGE et au CGEA.");
        }

        dossier.setIsConfidential(value);

        auditRecorder.addObservation(dossier,
                ObservationType.INTERNAL_NOTE,
                "Dossier marqué comme "
                        + (value ? "CONFIDENTIEL" : "NON CONFIDENTIEL")
                        + (request.getReason() != null
                        ? " — Motif : " + request.getReason() : ""),
                true, agent);

        Dossier saved = dossierRepository.save(dossier);
        log.info("Dossier {} — confidentiel: {}", saved.getNumber(), value);
        return enrichAndMaskDetail(saved);
    }

    @Override
    @Transactional
    public DossierResponse revokeWhistleblowerProtection(
            UUID dossierId,
            StatusTransitionRequest request) {

        Dossier dossier = getDossierOrThrow(dossierId);
        Agent agent = agentContextResolver.getCurrentAgent();

        if (!securityUtils.hasRole("CGE") && !securityUtils.hasRole("CGEA")) {
            throw new BusinessException(
                    "Seuls le CGE et le CGEA peuvent révoquer "
                            + "une protection lanceur d'alerte.");
        }

        if (request.getReason() == null || request.getReason().isBlank()) {
            throw new BusinessException("Le motif de révocation est obligatoire.");
        }

        if (dossier.getDeclarant() == null
                || !Boolean.TRUE.equals(dossier.getDeclarant().getProtectionRequested())) {
            throw new BusinessException(
                    "Ce dossier ne bénéficie d'aucune protection lanceur d'alerte active.");
        }

        dossier.getDeclarant().setProtectionRequested(false);
        declarantRepository.save(dossier.getDeclarant());

        auditRecorder.addObservation(dossier,
                ObservationType.CGE_DECISION,
                "⚠ Protection lanceur d'alerte RÉVOQUÉE par décision CGE/CGEA. "
                        + "Motif : " + request.getReason(),
                true, agent);

        log.warn("[SECURITE] Protection lanceur d'alerte RÉVOQUÉE — "
                        + "dossier: {}, par: {}, motif: {}",
                dossier.getNumber(), agent.getMatricule(), request.getReason());

        return enrichAndMaskDetail(dossierRepository.save(dossier));
    }


    // ════════════════════════════════════════════════════════════
    //  PRIORITÉ
    // ════════════════════════════════════════════════════════════

    @Override
    @Transactional
    public DossierResponse setPriority(UUID dossierId,
                                       SetPriorityRequest request,
                                       String ipAddress) {

        Dossier dossier = dossierRepository.findById(dossierId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Dossier introuvable : " + dossierId));

        if (!dossier.getVersion().equals(request.getVersion())) {
            throw new ConflictException(
                    "Ce dossier a été modifié par un autre agent. "
                            + "Rechargez la page avant de continuer.");
        }

        final List<DossierStatus> TERMINAUX = List.of(
                DossierStatus.CLOS, DossierStatus.CLASSE, DossierStatus.TRANSFERE);
        if (TERMINAUX.contains(dossier.getStatus())) {
            throw new BusinessException(
                    "Impossible de définir la priorité d'un dossier "
                            + dossier.getStatus().name() + " (terminal).");
        }

        if (request.getPriority() != DossierPriority.NORMAL) {
            if (request.getReason() == null || request.getReason().isBlank()) {
                throw new BusinessException(
                        "Le motif est obligatoire pour le niveau "
                                + request.getPriority().name() + ".");
            }
        }

        if (request.getDeadline() != null
                && request.getDeadline().isBefore(Instant.now())) {
            throw new BusinessException(
                    "La date d'échéance souhaitée doit être dans le futur.");
        }

        Agent agent = agentContextResolver.getCurrentAgent();
        DossierPriority oldPriority = dossier.getPriority() != null
                ? dossier.getPriority() : DossierPriority.NORMAL;

        dossier.setPriority(request.getPriority());
        dossier.setPriorityReason(request.getReason());
        dossier.setPriorityDeadline(request.getDeadline());
        dossier.setPrioritySetAt(Instant.now());
        dossier.setPrioritySetBy(agent);

        dossierRepository.save(dossier);

        String obs = String.format(
                "Priorité définie : %s → %s. Motif : %s%s",
                oldPriority.name(),
                request.getPriority().name(),
                request.getReason() != null ? request.getReason() : "Standard",
                request.getDeadline() != null
                        ? ". Échéance souhaitée : " + request.getDeadline() : ""
        );

        auditRecorder.addObservation(dossier, ObservationType.INTERNAL_NOTE, obs, true, agent);

        log.info("[Priorité] Dossier {} → {} par {}",
                dossier.getNumber(), request.getPriority(), agent.getNomComplet());

        return enrichAndMaskDetail(dossier);
    }


    // ════════════════════════════════════════════════════════════
    //  MÉTHODES PRIVÉES
    // ════════════════════════════════════════════════════════════

    private DossierResponse enrichAndMask(Dossier dossier) {
        DossierResponse response = dossierMapper.toResponse(dossier);

        if (dossier.getStatus() == DossierStatus.EN_ATTENTE_COMPLEMENT) {
            observationRepository
                    .findTopByDossierIdAndTypeOrderByCreatedAtDesc(
                            dossier.getId(),
                            ObservationType.COMPLEMENT_REQUEST)
                    .ifPresent(obs -> response.setComplementMotif(obs.getContent()));
        }

        return maskSensitiveData(response);
    }

    /**
     * Variante "détail" d'enrichAndMask — charge en plus les collections
     * *-to-many du dossier (témoins, parties visées, observations, pièces
     * jointes, notifications). Réservée à un seul dossier à la fois
     * (findById/findByAccessCode) : les inclure dans un flux paginé
     * provoquerait un N+1 sur chaque ligne.
     */
    private DossierResponse enrichAndMaskDetail(Dossier dossier) {
        DossierResponse response = enrichAndMask(dossier);

        response.setWitnesses(dossier.getWitnesses().stream()
                .map(dossierDetailsMapper::toResponse).toList());
        response.setTargetedParties(dossier.getTargetedParties().stream()
                .map(dossierDetailsMapper::toResponse).toList());
        response.setObservations(dossier.getObservations().stream()
                .map(dossierDetailsMapper::toResponse).toList());
        response.setAttachments(dossier.getAttachments().stream()
                .map(dossierDetailsMapper::toResponse).toList());
        response.setNotifications(dossier.getNotifications().stream()
                .map(dossierDetailsMapper::toResponse).toList());

        // Les collections viennent d'être peuplées après le premier passage de
        // maskSensitiveData — on le rejoue pour qu'un dossier confidentiel les
        // masque bien à un rôle non habilité.
        return maskSensitiveData(response);
    }

    private Dossier getDossierOrThrow(UUID id) {
        return dossierRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Dossier introuvable : " + id));
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
            response.setComplementMotif(null);
            response.setAttachments(null);
            response.setNotifications(null);
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