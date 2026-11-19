package gov.bf.ascelc.univers_audits.service.impl;

import gov.bf.ascelc.univers_audits.shared.utils.ProtectionAdresseIp;
import gov.bf.ascelc.univers_audits.enums.*;
import gov.bf.ascelc.univers_audits.mapper.DeclarantMapper;
import gov.bf.ascelc.univers_audits.mapper.DossierDetailsMapper;
import gov.bf.ascelc.univers_audits.mapper.DossierMapper;
import gov.bf.ascelc.univers_audits.model.dto.request.DossierCreateRequest;
import gov.bf.ascelc.univers_audits.model.dto.request.DossierUpdateRequest;
import gov.bf.ascelc.univers_audits.model.dto.request.SetPriorityRequest;
import gov.bf.ascelc.univers_audits.model.dto.request.StatusTransitionRequest;
import gov.bf.ascelc.univers_audits.model.dto.response.DossierResponse;
import gov.bf.ascelc.univers_audits.model.dto.response.DossierResume;
import gov.bf.ascelc.univers_audits.model.dto.response.WitnessResponse;
import gov.bf.ascelc.univers_audits.model.entity.*;
import gov.bf.ascelc.univers_audits.repository.*;
import gov.bf.ascelc.univers_audits.security.permission.AppPermission;
import gov.bf.ascelc.univers_audits.service.CurrentUserAuditor;
import gov.bf.ascelc.univers_audits.service.ProtectedIdentityExportAuditor;
import gov.bf.ascelc.univers_audits.service.DossierHabilitationService;
import gov.bf.ascelc.univers_audits.service.DossierService;
import gov.bf.ascelc.univers_audits.service.GeographieService;
import gov.bf.ascelc.univers_audits.service.GeographieService.OrigineGeographique;
import gov.bf.ascelc.univers_audits.service.NotificationDispatcherService;
import gov.bf.ascelc.univers_audits.service.ParametreDelaiService;
import gov.bf.ascelc.univers_audits.service.PortalConfigService;
import gov.bf.ascelc.univers_audits.shared.exceptions.BusinessException;
import gov.bf.ascelc.univers_audits.shared.exceptions.ConflictException;
import gov.bf.ascelc.univers_audits.shared.exceptions.ResourceNotFoundException;
import gov.bf.ascelc.univers_audits.shared.utils.AccessCodeGenerator;
import gov.bf.ascelc.univers_audits.shared.utils.AgentContextResolver;
import gov.bf.ascelc.univers_audits.shared.utils.DeadlineCalculator;
import gov.bf.ascelc.univers_audits.shared.utils.DossierAccessGuard;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
    private final GeographieService             geographieService;
    private final DossierAccessGuard            accessGuard;
    private final DossierHabilitationService    habilitationService;
    private final PortalConfigService           portalConfigService;
    private final EtudeOpportuniteRepository    etudeOpportuniteRepository;
    private final DecisionCGERepository         decisionCGERepository;
    private final DeadlineCalculator            deadlineCalculator;
    private final CurrentUserAuditor            currentUserAuditor;


    // ════════════════════════════════════════════════════════════
    //  LECTURE
    // ════════════════════════════════════════════════════════════

    @Override
    public DossierResume findResume(UUID id) {
        Dossier dossier = getDossierOrThrow(id);
        accessGuard.checkReadAccess(dossier);
        return new DossierResume(dossier.getReceptionDate(), dossier.getStatus());
    }

    @Override
    public DossierResponse findById(UUID id) {
        Dossier dossier = getDossierOrThrow(id);
        logSensitiveAccessIfProtected(dossier, "findById");
        accessGuard.checkReadAccess(dossier);
        return enrichAndMaskDetail(dossier);
    }

    @Override
    public DossierResponse findByAccessCode(String accessCode) {
        Dossier dossier = dossierRepository
                .findByAccessCode(accessCode)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Dossier introuvable avec ce code d'accès"));
        DossierResponse response = enrichAndMaskDetail(dossier);
        response.setEtudeOpportunite(null);
        response.setDecisionCGE(null);
        return response;
    }

    @Override
    public Page<DossierResponse> findByStatus(
            DossierStatus status, Pageable pageable) {
        return tracerListe(dossierRepository
                .findByStatus(status, pageable)
                .map(this::enrichAndMask));
    }

    @Override
    public Page<DossierResponse> findMyDossiers(Pageable pageable) {
        Agent agent = agentContextResolver.getCurrentAgent();
        return tracerListe(dossierRepository
                .findByAgentInChargeId(agent.getId(), pageable)
                .map(this::enrichAndMask));
    }

    @Override
    public Page<DossierResponse> findAll(Pageable pageable) {
        if (accessGuard.canSeeConfidential()) {
            return tracerListe(dossierRepository.findAll(pageable)
                    .map(this::enrichAndMask));
        }

        Agent agent = agentContextResolver.getCurrentAgent();
        log.debug("[Dossiers] Accès restreint — agent: {} voit uniquement ses dossiers habilités",
                agent.getMatricule());
        if (securityUtils.hasPermission(AppPermission.DOSSIER_ENREGISTRER)) {
            // le BRPD reçoit aussi les dépôts du portail qui attendent leur enregistrement
            return tracerListe(dossierRepository
                    .findAccessibleByAgentIdOrWithStatus(agent.getId(), DossierStatus.SOUMIS, pageable)
                    .map(this::enrichAndMask));
        }
        return tracerListe(dossierRepository
                .findAccessibleByAgentId(agent.getId(), pageable)
                .map(this::enrichAndMask));
    }

    @Override
    public Page<DossierResponse> findByReceptionDateBetween(
            Instant start, Instant end, Pageable pageable) {
        if (accessGuard.canSeeConfidential()) {
            return tracerListe(dossierRepository
                    .findByReceptionDateBetween(start, end, pageable)
                    .map(this::enrichAndMask));
        }

        Agent agent = agentContextResolver.getCurrentAgent();
        log.debug("[Dossiers] Accès restreint (période) — agent: {} voit uniquement "
                        + "ses dossiers habilités",
                agent.getMatricule());
        return tracerListe(dossierRepository
                .findAccessibleByAgentIdAndReceptionDateBetween(agent.getId(), start, end, pageable)
                .map(this::enrichAndMask));
    }


    // ════════════════════════════════════════════════════════════
    //  SOUMISSION
    // ════════════════════════════════════════════════════════════

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DossierResponse submit(DossierCreateRequest request, String ipAddress) {
        return enregistrer(request, ipAddress, false);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DossierResponse submitParAgent(DossierCreateRequest request, String ipAddress) {
        return enregistrer(request, ipAddress, true);
    }

    private DossierResponse enregistrer(DossierCreateRequest request, String ipAddress, boolean parAgent) {
        log.info("Nouvelle soumission — mode: {}", request.getSubmissionMode());

        if (request.getDeclarantData() != null
                && Boolean.TRUE.equals(
                request.getDeclarantData().getProtectionRequested())
                && !Boolean.TRUE.equals(
                request.getDeclarantData().getProtectionAcknowledged())) {
            throw new BusinessException(
                    "Vous devez confirmer avoir pris connaissance des conditions "
                            + "de la protection lanceur d'alerte "
                            + "avant de pouvoir la demander.");
        }

        Declarant declarant = resolveDeclarant(request);
        if (declarant == null) {
            throw new BusinessException(
                    "Le déclarant est requis pour déterminer la nature de la saisine.");
        }

        boolean anonymousRequested = Boolean.TRUE.equals(request.getAnonymous());
        // Le citoyen ne choisit jamais la nature ni la typologie : seul l'agent qui enregistre le peut.
        TypeSaisine natureSaisine = parAgent && request.getNatureSaisineChoisie() != null
                ? natureSaisineResolver.resolve(declarant.getTypeDeclarant(), request.getQuality(),
                        anonymousRequested, request.getNatureSaisineChoisie())
                : natureSaisineResolver.resolve(declarant.getTypeDeclarant(), request.getQuality(),
                        anonymousRequested);

        Dossier dossier = dossierMapper.toEntity(request);
        dossier.setDeclarant(declarant);
        dossier.setType(natureSaisine);
        OrigineGeographique origine = geographieService.resoudreOrigine(request.getPaysOrigine(),
                request.getOrigineRegionId(), request.getOrigineProvinceId(), request.getOrigineCommuneId(),
                anonymousRequested);
        dossier.setOriginePays(origine.pays());
        dossier.setOrigineRegion(origine.region());
        dossier.setOrigineProvince(origine.province());
        dossier.setOrigineCommune(origine.commune());
        dossier.setRegionOrigine(origine.zone());
        dossier.setTypologieInstitutionnelle(parAgent ? request.getTypologieInstitutionnelle() : null);
        dossier.setAnonymous(anonymousRequested);
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
        if (dossier.getAutreInstitutionSaisie() == null) {
            dossier.setAutreInstitutionSaisie(false);
        }
        if (dossier.getDecisionJusticeExistante() == null) {
            dossier.setDecisionJusticeExistante(false);
        }

        boolean protectionRequested = declarant != null
                && Boolean.TRUE.equals(declarant.getProtectionRequested());

        if (protectionRequested) {
            dossier.setIsConfidential(true);
            // L'adresse de connexion n'est volontairement pas écrite : elle trahirait le lanceur d'alerte.
            log.warn("[SECURITE] Protection lanceur d'alerte — dossier marqué confidentiel d'office à la soumission.");
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
                    portalConfigService.resolveNotificationText(
                            "notif_subject_audio_alert", Map.of()),
                    portalConfigService.resolveNotificationText(
                            "notif_content_audio_alert", Map.of()),
                    Instant.now());
        }

        if (protectionRequested) {
            createNotification(saved,
                    NotificationType.INTERNAL_ALERT,
                    NotificationChannel.PORTAL,
                    portalConfigService.resolveNotificationText(
                            "notif_subject_protection_submitted", Map.of()),
                    portalConfigService.resolveNotificationText(
                            "notif_content_protection_submitted", Map.of()),
                    Instant.now());
        }

        auditRecorder.recordStatusChange(saved, null, DossierStatus.SOUMIS,
                "Dossier soumis via " + request.getSubmissionMode(),
                null, ProtectionAdresseIp.aConserver(anonymousRequested, protectionRequested, ipAddress));

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
        Instant receptionDate = Instant.now();
        Instant acknowledgmentDeadline = resolveDeadline(receptionDate, "ACCUSE_RECEPTION");
        Instant additionalInfoDeadline = resolveDeadline(receptionDate, "DEMANDE_COMPLEMENT");
        dossier.registerReception(agent, receptionDate, acknowledgmentDeadline, additionalInfoDeadline);

        habilitationService.grant(dossier, agent, HabilitationSource.AGENT_IN_CHARGE,
                agent, "Agent en charge du dossier (enregistrement BRPD)");

        auditRecorder.addObservation(dossier,
                ObservationType.INTERNAL_NOTE,
                "Dossier enregistré. Numéro attribué : " + number,
                false, agent);

        if (dossier.getDeclarant() != null
                && Boolean.TRUE.equals(
                dossier.getDeclarant().getProtectionRequested())) {

            auditRecorder.addObservation(dossier,
                    ObservationType.INTERNAL_NOTE,
                    "⚠ PROTECTION LANCEUR D'ALERTE DEMANDÉE. "
                            + "Ce déclarant a demandé une protection officielle. "
                            + "Aucune information permettant son identification "
                            + "ne doit être divulguée.",
                    true, agent);

            createNotification(dossier,
                    NotificationType.INTERNAL_ALERT,
                    NotificationChannel.PORTAL,
                    portalConfigService.resolveNotificationText(
                            "notif_subject_protection_registered", Map.of("numero", number)),
                    portalConfigService.resolveNotificationText(
                            "notif_content_protection_registered", Map.of("numero", number)),
                    Instant.now());

            log.warn("[SECURITE] PROTECTION LANCEUR D'ALERTE activée — "
                            + "dossier: {}, agent: {}, IP: {}",
                    number, agent.getMatricule(), ipAddress);
        }

        createNotification(dossier,
                NotificationType.RECEIPT_B4,
                NotificationChannel.PORTAL,
                portalConfigService.resolveNotificationText(
                        "notif_subject_receipt_b4", Map.of("numero", number)),
                portalConfigService.resolveNotificationText(
                        "notif_content_receipt_b4", Map.of("numero", number)),
                Instant.now());

        createNotification(dossier,
                NotificationType.ACKNOWLEDGMENT_B5,
                resolveNotificationChannel(dossier),
                portalConfigService.resolveNotificationText(
                        "notif_subject_acknowledgment_b5", Map.of()),
                portalConfigService.resolveNotificationText(
                        "notif_content_acknowledgment_b5", Map.of()),
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
                portalConfigService.resolveNotificationText(
                        "notif_subject_complement_request", Map.of()),
                portalConfigService.resolveNotificationText(
                        "notif_content_complement_request",
                        Map.of("motif", request.getReason())),
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

        if (!etudeOpportuniteRepository.existsByDossierId(dossierId)) {
            throw new BusinessException(
                    "Impossible de soumettre au CTADP sans étude d'opportunité "
                            + "préalable");
        }

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
        refuserLAuteurDeLEtude(dossier, agent);

        dossier.setStatus(DossierStatus.RECEVABLE);
        dossier.setEligibilityDecisionDate(Instant.now());

        auditRecorder.addObservation(dossier,
                ObservationType.CGE_DECISION,
                "Dossier déclaré RECEVABLE par le CGE. "
                        + (request.getReason() != null ? request.getReason() : ""),
                true, agent);

        recordDecisionCGE(dossier, RecommandationCtadp.VALIDATION_INVESTIGATION,
                request.getReason(), agent);

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
        refuserLAuteurDeLEtude(dossier, agent);

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

        recordDecisionCGE(dossier, RecommandationCtadp.CLASSEMENT,
                request.getReason(), agent);

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
        refuserLAuteurDeLEtude(dossier, agent);

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

        recordDecisionCGE(dossier, RecommandationCtadp.TRANSMISSION_INSTITUTION_PARTENAIRE,
                request.getReason(), agent);

        Dossier saved = dossierRepository.save(dossier);

        notificationDispatcher.dispatchTransferExternal(saved, request.getTransferInstitution());

        auditRecorder.recordStatusChange(saved,
                DossierStatus.EN_REVUE_CTADP, DossierStatus.TRANSFERE,
                request.getReason(), agent, ipAddress);

        return enrichAndMaskDetail(saved);
    }

    @Override
    @Transactional
    public DossierResponse orientAdministratif(
            UUID dossierId,
            StatusTransitionRequest request,
            String ipAddress) {

        Dossier dossier = getDossierOrThrow(dossierId);
        validateTransition(dossier, DossierStatus.ORIENTEE_ADMINISTRATIF);
        Agent agent = agentContextResolver.getCurrentAgent();
        refuserLAuteurDeLEtude(dossier, agent);

        if (request.getReason() == null || request.getReason().isBlank()) {
            throw new BusinessException(
                    "Le motif d'orientation administrative est obligatoire");
        }

        dossier.setStatus(DossierStatus.ORIENTEE_ADMINISTRATIF);
        dossier.setEligibilityDecisionDate(Instant.now());

        auditRecorder.addObservation(dossier,
                ObservationType.CGE_DECISION,
                "Dossier orienté vers l'autorité hiérarchique (irrégularité). Motif : "
                        + request.getReason(),
                true, agent);

        recordDecisionCGE(dossier, RecommandationCtadp.ORIENTATION_ADMINISTRATIVE,
                request.getReason(), agent);

        Dossier saved = dossierRepository.save(dossier);

        notificationDispatcher.dispatchStatusUpdate(saved, "ORIENTEE_ADMINISTRATIF", request.getReason());

        auditRecorder.recordStatusChange(saved,
                DossierStatus.EN_REVUE_CTADP, DossierStatus.ORIENTEE_ADMINISTRATIF,
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
        accessGuard.checkReadAccess(dossier);

        if (dossier.isClosed()) {
            throw new BusinessException(
                    "Un dossier clôturé ne peut plus être modifié");
        }

        boolean isProtected = dossier.getDeclarant() != null
                && Boolean.TRUE.equals(dossier.getDeclarant().getProtectionRequested());
        boolean peutTraiterLanceurAlerte =
                securityUtils.hasPermission(AppPermission.LANCEUR_ALERTE_PROTEGE_TRAITER);

        if (isProtected && !peutTraiterLanceurAlerte) {
            throw new BusinessException(
                    "Modification restreinte — ce dossier concerne un lanceur "
                            + "d'alerte sous protection. "
                            + "Seuls les agents habilités à traiter ces dossiers peuvent le modifier.");
        }

        Map<String, Object> avant = champsSuivis(dossier);
        dossierMapper.updateEntity(request, dossier);
        appliquerNatureEtTypologie(request, dossier);

        if (dossier.getIsConfidential() == null) {
            dossier.setIsConfidential(false);
        }

        Dossier enregistre = dossierRepository.save(dossier);
        List<String> modifies = champsModifies(avant, champsSuivis(enregistre));
        if (!modifies.isEmpty()) {
            // Trace de qui a complété la fiche et quels champs : jamais leur contenu (il peut être sensible).
            auditRecorder.addObservation(enregistre, ObservationType.INTERNAL_NOTE,
                    "Fiche complétée : " + String.join(", ", modifies) + ".", false,
                    agentContextResolver.getCurrentAgent());
        }
        return enrichAndMaskDetail(enregistre);
    }

    /** Valeurs des champs qu'un agent peut compléter, pour savoir lesquels ont réellement changé. */
    private static Map<String, Object> champsSuivis(Dossier d) {
        Map<String, Object> champs = new java.util.LinkedHashMap<>();
        champs.put("objet", d.getObject());
        champs.put("description", d.getDescription());
        champs.put("motifs", d.getMotifs());
        champs.put("lieu des faits", d.getIncidentLocation());
        champs.put("période", d.getIncidentPeriod());
        champs.put("montant estimé", d.getEstimatedLoss());
        champs.put("numéro de courrier", d.getNumeroCourrier());
        champs.put("nature", d.getType());
        champs.put("type d'institution", d.getTypologieInstitutionnelle());
        champs.put("confidentialité", d.getIsConfidential());
        return champs;
    }

    private static List<String> champsModifies(Map<String, Object> avant, Map<String, Object> apres) {
        List<String> modifies = new ArrayList<>();
        apres.forEach((nom, valeur) -> {
            if (!java.util.Objects.equals(avant.get(nom), valeur)) {
                modifies.add(nom);
            }
        });
        return modifies;
    }

    /**
     * La nature et le type d'institution se choisissent à l'enregistrement ou en complétant la fiche : le citoyen
     * n'a que plainte ou dénonciation. Un signalement d'autorité ou une auto-saisine garde sa nature.
     */
    private static void appliquerNatureEtTypologie(DossierUpdateRequest request, Dossier dossier) {
        TypeSaisine choisie = request.getNatureSaisineChoisie();
        if (choisie != null && choisie != dossier.getType()) {
            if (dossier.getType() == TypeSaisine.SIGNALEMENT || dossier.getType() == TypeSaisine.AUTO_SAISINE) {
                throw new BusinessException(
                        "La nature d'un signalement d'autorité ou d'une auto-saisine ne peut pas être modifiée.");
            }
            if (!NatureSaisineResolver.CHOIX_DE_L_AGENT.contains(choisie)) {
                throw new BusinessException(
                        "Nature non autorisée : choisissez plainte, dénonciation, plainte-dénonciation "
                                + "ou demande d'intervention.");
            }
            dossier.setType(choisie);
        }
        if (request.getTypologieInstitutionnelle() != null) {
            dossier.setTypologieInstitutionnelle(request.getTypologieInstitutionnelle());
        }
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
        boolean peutTraiterLanceurAlerte =
                securityUtils.hasPermission(AppPermission.LANCEUR_ALERTE_PROTEGE_TRAITER);

        if (isProtected && !value && !peutTraiterLanceurAlerte) {
            throw new BusinessException(
                    "Impossible de retirer la confidentialité d'un dossier "
                            + "lanceur d'alerte protégé. "
                            + "Cette action est réservée aux agents habilités à traiter ces dossiers.");
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

        if (!securityUtils.hasPermission(AppPermission.LANCEUR_ALERTE_PROTEGE_TRAITER)) {
            throw new BusinessException(
                    "Seuls les agents habilités à traiter les dossiers de lanceurs d'alerte "
                            + "peuvent révoquer une protection.");
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
        // Toute réponse de détail (ouverture, modification, confidentialité, transitions) passe ici : la trace
        // de l'identité d'un lanceur d'alerte protégé ne dépend donc pas de l'écran qui la demande.
        recordProtectedIdentityConsultation(dossier);
        DossierResponse response = enrichAndMask(dossier);

        response.setWitnesses(dossier.getWitnesses().stream()
                .map(dossierDetailsMapper::toResponse).toList());
        response.setEtudeOpportunite(
                etudeOpportuniteRepository.findByDossierId(dossier.getId())
                        .map(dossierDetailsMapper::toResponse)
                        .orElse(null));
        response.setDecisionCGE(
                decisionCGERepository.findByDossierId(dossier.getId())
                        .map(dossierDetailsMapper::toResponse)
                        .orElse(null));
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

    private Instant resolveDeadline(Instant from, String delaiCode) {
        int delaiJours = parametreDelaiService.resolveDelaiJours(delaiCode);
        boolean joursOuvrables = parametreDelaiService.resolveJoursOuvrables(delaiCode);
        return joursOuvrables
                ? deadlineCalculator.addBusinessDays(from, delaiJours)
                : deadlineCalculator.addCalendarDays(from, delaiJours);
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
            case RECEVABLE, IRRECEVABLE, TRANSFERE, ORIENTEE_ADMINISTRATIF ->
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
                            || dossier.getStatus() == DossierStatus.TRANSFERE
                            || dossier.getStatus() == DossierStatus.ORIENTEE_ADMINISTRATIF;
            default -> false;
        };

        if (!allowed) {
            throw new BusinessException(
                    "Transition invalide : "
                            + dossier.getStatus() + " → " + target);
        }
    }

    /**
     * Séparation des pouvoirs : celui qui a rédigé l'étude d'opportunité (créée ou modifiée en dernier par lui) ne prend
     * pas la décision sur ce dossier (recevable, irrecevable, transmission, orientation administrative). L'auteur est
     * l'identifiant Keycloak que l'audit des entités enregistre déjà ; sans étude, la règle ne s'applique pas.
     */
    private void refuserLAuteurDeLEtude(Dossier dossier, Agent decideur) {
        if (decideur == null || decideur.getKeycloakId() == null) {
            return;
        }
        etudeOpportuniteRepository.findByDossierId(dossier.getId()).ifPresent(etude -> {
            String idDuDecideur = decideur.getKeycloakId();
            if (idDuDecideur.equals(etude.getCreatedById()) || idDuDecideur.equals(etude.getUpdatedById())) {
                throw new BusinessException("Vous avez rédigé l'étude d'opportunité de ce dossier : la décision doit être "
                        + "prise par une autre personne.");
            }
        });
    }

    private void recordDecisionCGE(Dossier dossier,
                                   RecommandationCtadp decision,
                                   String motif,
                                   Agent agent) {
        DecisionCGE record = decisionCGERepository.findByDossierId(dossier.getId())
                .orElseGet(() -> DecisionCGE.builder().dossier(dossier).build());
        record.setDecision(decision);
        record.setMotif(motif);
        record.setDateDecision(Instant.now());
        record.setAgentCGE(agent);
        decisionCGERepository.save(record);
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

    /**
     * Trace dans le journal d'audit qui a consulté l'identité d'un lanceur d'alerte protégé. N'écrit que si
     * l'identité est réellement renvoyée en clair (l'agent détient la permission) et que l'accès au dossier est
     * accordé : les agents qui ne voient qu'un nom masqué ne laissent pas de trace.
     */
    private void recordProtectedIdentityConsultation(Dossier dossier) {
        if (dossier.getDeclarant() == null
                || !Boolean.TRUE.equals(dossier.getDeclarant().getProtectionRequested())
                || !securityUtils.hasPermission(AppPermission.LANCEUR_ALERTE_PROTEGE_TRAITER)) {
            return;
        }
        currentUserAuditor.record("CONSULTER_IDENTITE_LANCEUR_ALERTE", "DOSSIER", dossier.getId().toString(),
                "Consultation de l'identité du lanceur d'alerte protégé — dossier "
                        + (dossier.getNumber() != null ? dossier.getNumber() : dossier.getId()));
    }

    /**
     * Les listes renvoient aussi l'identité en clair d'un lanceur d'alerte protégé à un agent habilité : comme pour le
     * détail, la consultation est écrite au journal d'audit, une seule entrée par liste (nombre et numéros de dossier).
     */
    private Page<DossierResponse> tracerListe(Page<DossierResponse> page) {
        List<DossierResponse> exposes = page.getContent().stream()
                .filter(r -> r.getDeclarant() != null
                        && Boolean.TRUE.equals(r.getDeclarant().getProtectionRequested())
                        && ProtectedIdentityExportAuditor.identiteEnClair(r.getDeclarant()))
                .toList();
        if (!exposes.isEmpty()) {
            String numeros = exposes.stream().map(DossierResponse::getNumber).filter(java.util.Objects::nonNull)
                    .distinct().limit(5).collect(java.util.stream.Collectors.joining(", "));
            currentUserAuditor.record("CONSULTER_IDENTITE_LANCEUR_ALERTE", "DOSSIER", null,
                    "Consultation de l'identité de " + exposes.size()
                            + " lanceur(s) d'alerte protégé(s) dans une liste de dossiers"
                            + (numeros.isEmpty() ? "" : " — dossier(s) : " + numeros));
        }
        return page;
    }

    private DossierResponse maskSensitiveData(DossierResponse response) {

        boolean canSeeConfidential = accessGuard.canSeeConfidential();

        if (Boolean.TRUE.equals(response.getIsConfidential())
                && !canSeeConfidential) {
            response.setDeclarant(null);
            response.setDescription("** Contenu confidentiel — accès restreint **");
            response.setMotifs(null);
            response.setIncidentLocation(null);
            response.setEstimatedLoss(null);
            response.setObservations(null);
            response.setWitnesses(null);
            response.setEtudeOpportunite(null);
            response.setDecisionCGE(null);
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
                && !securityUtils.hasPermission(AppPermission.LANCEUR_ALERTE_PROTEGE_TRAITER)) {
            response.getDeclarant().setFirstName(null);
            response.getDeclarant().setLastName(null);
            response.getDeclarant().setEmail(null);
            response.getDeclarant().setPhoneNumber(null);
            response.getDeclarant().setAddress(null);
            response.getDeclarant().setCommune(null);
            response.getDeclarant().setProvince(null);
            response.getDeclarant().setCellulaire(null);
            response.getDeclarant().setLocalite(null);
            response.getDeclarant().setProfession(null);
            response.getDeclarant().setOrganizationName(null);
            response.getDeclarant().setDisplayName(
                    "Lanceur d'alerte protégé");
            log.debug("Dossier {} — identité lanceur d'alerte masquée",
                    response.getNumber());
        }

        if (response.getDeclarant() != null
                && Boolean.TRUE.equals(response.getAnonymous())) {
            response.getDeclarant().setFirstName(null);
            response.getDeclarant().setLastName(null);
            response.getDeclarant().setEmail(null);
            response.getDeclarant().setPhoneNumber(null);
            response.getDeclarant().setAddress(null);
            response.getDeclarant().setCommune(null);
            response.getDeclarant().setProvince(null);
            response.getDeclarant().setCellulaire(null);
            response.getDeclarant().setLocalite(null);
            response.getDeclarant().setOrganizationName(null);
            response.getDeclarant().setDisplayName("Déclarant anonyme");
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