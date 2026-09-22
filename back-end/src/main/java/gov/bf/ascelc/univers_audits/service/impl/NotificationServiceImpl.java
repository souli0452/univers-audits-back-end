package gov.bf.ascelc.univers_audits.service.impl;

import gov.bf.ascelc.univers_audits.enums.DossierStatus;
import gov.bf.ascelc.univers_audits.enums.NotificationChannel;
import gov.bf.ascelc.univers_audits.enums.NotificationStatus;
import gov.bf.ascelc.univers_audits.enums.NotificationType;
import gov.bf.ascelc.univers_audits.mapper.DossierDetailsMapper;
import gov.bf.ascelc.univers_audits.model.dto.response.NotificationResponse;
import gov.bf.ascelc.univers_audits.model.entity.Agent;
import gov.bf.ascelc.univers_audits.model.entity.DemandeDocuments;
import gov.bf.ascelc.univers_audits.model.entity.Dossier;
import gov.bf.ascelc.univers_audits.model.entity.Investigation;
import gov.bf.ascelc.univers_audits.model.entity.Notification;
import gov.bf.ascelc.univers_audits.repository.AgentRepository;
import gov.bf.ascelc.univers_audits.repository.DemandeDocumentsRepository;
import gov.bf.ascelc.univers_audits.repository.DossierRepository;
import gov.bf.ascelc.univers_audits.repository.InvestigationRepository;
import gov.bf.ascelc.univers_audits.repository.NotificationRepository;
import gov.bf.ascelc.univers_audits.service.KeycloakAdminService;
import gov.bf.ascelc.univers_audits.service.NotificationService;
import gov.bf.ascelc.univers_audits.service.ParametreDelaiService;
import gov.bf.ascelc.univers_audits.service.PortalConfigService;
import gov.bf.ascelc.univers_audits.shared.utils.DeadlineCalculator;
import gov.bf.ascelc.univers_audits.shared.exceptions.BusinessException;
import gov.bf.ascelc.univers_audits.shared.exceptions.ResourceNotFoundException;
import gov.bf.ascelc.univers_audits.shared.utils.DossierAccessGuard;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository notificationRepository;
    private final DossierRepository      dossierRepository;
    private final AgentRepository        agentRepository;
    private final DossierDetailsMapper   detailsMapper;
    private final DossierAccessGuard     accessGuard;
    private final PortalConfigService    portalConfigService;
    private final DeadlineCalculator     deadlineCalculator;
    private final DemandeDocumentsRepository demandeDocumentsRepository;
    private final InvestigationRepository    investigationRepository;
    private final ParametreDelaiService      parametreDelaiService;
    private final KeycloakAdminService       keycloakAdminService;


    @Override
    public Page<NotificationResponse> findByDossierId(
            UUID dossierId, Pageable pageable) {

        Dossier dossier = accessGuard.getDossierOrThrow(dossierId);
        accessGuard.checkReadAccess(dossier);

        // Un dossier confidentiel masque entièrement ses notifications aux rôles
        // non habilités — même comportement que Witness/TargetedPartyServiceImpl.
        if (Boolean.TRUE.equals(dossier.getIsConfidential())
                && !accessGuard.canSeeConfidential()) {
            return Page.empty(pageable);
        }

        return notificationRepository
                .findByDossierId(dossierId, pageable)
                .map(detailsMapper::toResponse);
    }

    @Override
    public Page<NotificationResponse> findMyNotifications(
            String keycloakId, boolean unreadOnly, Pageable pageable) {

        Agent agent = resolveAgentOrThrow(keycloakId);

        return unreadOnly
                ? notificationRepository
                        .findUnreadByAgentOrRecipient(agent.getId(), keycloakId, pageable)
                        .map(detailsMapper::toResponse)
                : notificationRepository
                        .findByAgentOrRecipient(agent.getId(), keycloakId, pageable)
                        .map(detailsMapper::toResponse);
    }

    @Override
    public long countUnread(String keycloakId) {
        Agent agent = resolveAgentOrThrow(keycloakId);
        return notificationRepository
                .countUnreadByAgentOrRecipient(agent.getId(), keycloakId);
    }

    @Override
    @Transactional
    public void markAsRead(UUID notificationId, String keycloakId) {
        Agent agent = resolveAgentOrThrow(keycloakId);
        Notification notification = getOrThrow(notificationId);

        boolean isOwner = false;
        if (notification.getDossier() != null
                && notification.getDossier().getAgentInCharge() != null) {
            isOwner = notification.getDossier()
                    .getAgentInCharge().getId().equals(agent.getId());
        }
        if (!isOwner && keycloakId.equals(notification.getRecipient())) {
            isOwner = true;
        }

        if (!isOwner) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "Vous n'êtes pas destinataire de cette notification");
        }

        notification.markAsRead();
        notificationRepository.save(notification);
    }

    @Override
    @Transactional
    public void markAllAsRead(String keycloakId) {
        Agent agent = resolveAgentOrThrow(keycloakId);
        notificationRepository.markAllReadByAgentOrRecipient(
                agent.getId(), keycloakId, Instant.now());
    }

    @Override
    public Page<NotificationResponse> findPending(Pageable pageable) {
        return notificationRepository
                .findByStatusIn(List.of(NotificationStatus.PENDING), pageable)
                .map(detailsMapper::toResponse);
    }

    private Agent resolveAgentOrThrow(String keycloakId) {
        return agentRepository.findByKeycloakId(keycloakId)
                .orElseThrow(() -> new ResourceNotFoundException("Agent introuvable"));
    }

    @Override
    public List<NotificationResponse> findOverdue() {
        return notificationRepository
                .findOverdue(Instant.now())
                .stream()
                .map(detailsMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    public List<NotificationResponse> findPending() {
        return notificationRepository
                .findByStatus(NotificationStatus.PENDING)
                .stream()
                .map(detailsMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public NotificationResponse sendNow(UUID notificationId) {
        Notification notif = getOrThrow(notificationId);

        if (notif.getStatus() == NotificationStatus.SENT) {
            throw new BusinessException("Cette notification a déjà été envoyée");
        }
        if (notif.getStatus() == NotificationStatus.CANCELLED) {
            throw new BusinessException("Cette notification a été annulée");
        }

        doSend(notif);
        return detailsMapper.toResponse(notificationRepository.save(notif));
    }

    @Override
    @Transactional
    public NotificationResponse cancel(UUID notificationId, String reason) {
        Notification notif = getOrThrow(notificationId);

        if (notif.getStatus() == NotificationStatus.SENT) {
            throw new BusinessException(
                    "Impossible d'annuler une notification déjà envoyée");
        }

        notif.setStatus(NotificationStatus.CANCELLED);
        notif.setErrorMessage("Annulée manuellement : " + reason);
        return detailsMapper.toResponse(notificationRepository.save(notif));
    }

    @Override
    @Transactional
    public NotificationResponse retry(UUID notificationId) {
        Notification notif = getOrThrow(notificationId);

        if (!notif.canRetry()) {
            throw new BusinessException(
                    "Maximum de tentatives atteint (3) ou notification non échouée");
        }

        doSend(notif);
        return detailsMapper.toResponse(notificationRepository.save(notif));
    }


    @Override
    @Scheduled(fixedDelay = 900_000)
    @Transactional
    public void processPendingNotifications() {
        log.info("[Notification] Traitement des notifications en attente...");

        List<Notification> overdueNotifs =
                notificationRepository.findOverdue(Instant.now());

        int sent = 0, failed = 0;

        for (Notification notif : overdueNotifs) {
            try {
                doSend(notif);
                notificationRepository.save(notif);
                sent++;
            } catch (Exception e) {
                log.error("[Notification] Échec envoi {} : {}",
                        notif.getId(), e.getMessage());
                failed++;
            }
        }

        List<Notification> retryable = notificationRepository.findRetryable();
        for (Notification notif : retryable) {
            try {
                doSend(notif);
                notificationRepository.save(notif);
                sent++;
            } catch (Exception e) {
                log.error("[Notification] Échec relance {} : {}",
                        notif.getId(), e.getMessage());
                failed++;
            }
        }

        if (sent > 0 || failed > 0) {
            log.info("[Notification] Traitement terminé — envoyées: {}, échouées: {}",
                    sent, failed);
        }
    }


    @Override
    @Scheduled(cron = "0 0 8 * * MON-FRI")
    @Transactional
    public void sendDeadlineAlerts() {
        log.info("[Notification] Envoi des alertes de délai (échéance dépassée et J-3)...");

        Instant now = Instant.now();
        Instant in3Days = deadlineCalculator.addCalendarDays(now, 3);

        List<Dossier> overdueAcknowledgments =
                dossierRepository.findOverdueAcknowledgments(now);

        for (Dossier dossier : overdueAcknowledgments) {
            boolean alreadyAlerted = notificationRepository
                    .existsByDossierIdAndType(
                            dossier.getId(),
                            NotificationType.DEADLINE_ALERT);

            if (!alreadyAlerted) {
                Notification alert = Notification.builder()
                        .dossier(dossier)
                        .type(NotificationType.DEADLINE_ALERT)
                        .channel(NotificationChannel.PORTAL)
                        .subject(portalConfigService.resolveNotificationText(
                                "notif_subject_deadline_ar",
                                Map.of("numero", dossier.getNumber())))
                        .content(portalConfigService.resolveNotificationText(
                                "notif_content_deadline_ar",
                                Map.of("numero", dossier.getNumber())))
                        .scheduledAt(Instant.now())
                        .build();

                notificationRepository.save(alert);
                log.warn("[Notification] Alerte AR créée — dossier: {}",
                        dossier.getNumber());
            }
        }

        List<Dossier> overdueComplements =
                dossierRepository.findOverdueComplementRequests(now);

        for (Dossier dossier : overdueComplements) {
            boolean alreadyAlerted = notificationRepository
                    .existsByDossierIdAndType(
                            dossier.getId(),
                            NotificationType.INTERNAL_ALERT);

            if (!alreadyAlerted) {
                Notification alert = Notification.builder()
                        .dossier(dossier)
                        .type(NotificationType.INTERNAL_ALERT)
                        .channel(NotificationChannel.PORTAL)
                        .subject(portalConfigService.resolveNotificationText(
                                "notif_subject_deadline_complement",
                                Map.of("numero", dossier.getNumber())))
                        .content(portalConfigService.resolveNotificationText(
                                "notif_content_deadline_complement",
                                Map.of("numero", dossier.getNumber())))
                        .scheduledAt(Instant.now())
                        .build();

                notificationRepository.save(alert);
                log.warn("[Notification] Alerte complément créée — dossier: {}",
                        dossier.getNumber());
            }
        }

        List<Dossier> overdueInvestigations =
                dossierRepository.findOverdueInvestigations(now);

        for (Dossier dossier : overdueInvestigations) {
            boolean alreadyAlerted = notificationRepository
                    .existsByDossierIdAndType(
                            dossier.getId(),
                            NotificationType.INVESTIGATION_ALERT);

            if (!alreadyAlerted) {
                Notification alert = Notification.builder()
                        .dossier(dossier)
                        .type(NotificationType.INVESTIGATION_ALERT)
                        .channel(NotificationChannel.PORTAL)
                        .subject(portalConfigService.resolveNotificationText(
                                "notif_subject_deadline_investigation",
                                Map.of("numero", dossier.getNumber())))
                        .content(portalConfigService.resolveNotificationText(
                                "notif_content_deadline_investigation",
                                Map.of("numero", dossier.getNumber())))
                        .scheduledAt(Instant.now())
                        .build();

                notificationRepository.save(alert);
                log.warn("[Notification] Alerte investigation créée — dossier: {}",
                        dossier.getNumber());
            }
        }

        List<Dossier> dueAcknowledgments =
                dossierRepository.findAcknowledgmentsDueWithin(now, in3Days);

        for (Dossier dossier : dueAcknowledgments) {
            boolean alreadyAlerted = notificationRepository
                    .existsByDossierIdAndType(
                            dossier.getId(),
                            NotificationType.DEADLINE_ALERT_J3);

            if (!alreadyAlerted) {
                Notification alert = Notification.builder()
                        .dossier(dossier)
                        .type(NotificationType.DEADLINE_ALERT_J3)
                        .channel(NotificationChannel.PORTAL)
                        .subject(portalConfigService.resolveNotificationText(
                                "notif_subject_deadline_ar_j3",
                                Map.of("numero", dossier.getNumber())))
                        .content(portalConfigService.resolveNotificationText(
                                "notif_content_deadline_ar_j3",
                                Map.of("numero", dossier.getNumber())))
                        .scheduledAt(Instant.now())
                        .build();

                notificationRepository.save(alert);
                log.warn("[Notification] Alerte AR J-3 créée — dossier: {}",
                        dossier.getNumber());
            }
        }

        List<Dossier> dueComplements =
                dossierRepository.findComplementsDueWithin(now, in3Days);

        for (Dossier dossier : dueComplements) {
            boolean alreadyAlerted = notificationRepository
                    .existsByDossierIdAndType(
                            dossier.getId(),
                            NotificationType.COMPLEMENT_ALERT_J3);

            if (!alreadyAlerted) {
                Notification alert = Notification.builder()
                        .dossier(dossier)
                        .type(NotificationType.COMPLEMENT_ALERT_J3)
                        .channel(NotificationChannel.PORTAL)
                        .subject(portalConfigService.resolveNotificationText(
                                "notif_subject_deadline_complement_j3",
                                Map.of("numero", dossier.getNumber())))
                        .content(portalConfigService.resolveNotificationText(
                                "notif_content_deadline_complement_j3",
                                Map.of("numero", dossier.getNumber())))
                        .scheduledAt(Instant.now())
                        .build();

                notificationRepository.save(alert);
                log.warn("[Notification] Alerte complément J-3 créée — dossier: {}",
                        dossier.getNumber());
            }
        }

        List<Dossier> dueInvestigations =
                dossierRepository.findInvestigationsDueWithin(now, in3Days);

        for (Dossier dossier : dueInvestigations) {
            boolean alreadyAlerted = notificationRepository
                    .existsByDossierIdAndType(
                            dossier.getId(),
                            NotificationType.INVESTIGATION_ALERT_J3);

            if (!alreadyAlerted) {
                Notification alert = Notification.builder()
                        .dossier(dossier)
                        .type(NotificationType.INVESTIGATION_ALERT_J3)
                        .channel(NotificationChannel.PORTAL)
                        .subject(portalConfigService.resolveNotificationText(
                                "notif_subject_deadline_investigation_j3",
                                Map.of("numero", dossier.getNumber())))
                        .content(portalConfigService.resolveNotificationText(
                                "notif_content_deadline_investigation_j3",
                                Map.of("numero", dossier.getNumber())))
                        .scheduledAt(Instant.now())
                        .build();

                notificationRepository.save(alert);
                log.warn("[Notification] Alerte investigation J-3 créée — dossier: {}",
                        dossier.getNumber());
            }
        }

        List<gov.bf.ascelc.univers_audits.model.entity.DemandeDocuments> overdueDemandeDocuments =
                demandeDocumentsRepository.findOverdue(now);

        for (var demande : overdueDemandeDocuments) {
            gov.bf.ascelc.univers_audits.model.entity.Dossier dossier =
                    demande.getInvestigation().getDossier();
            boolean alreadyAlerted = notificationRepository
                    .existsByDemandeDocumentsIdAndTypeAndCreatedAtAfter(
                            demande.getId(),
                            NotificationType.DEMANDE_DOCUMENTS_ALERT,
                            demande.getSentAt());

            if (!alreadyAlerted) {
                Notification alert = Notification.builder()
                        .dossier(dossier)
                        .demandeDocuments(demande)
                        .type(NotificationType.DEMANDE_DOCUMENTS_ALERT)
                        .channel(NotificationChannel.PORTAL)
                        .subject(portalConfigService.resolveNotificationText(
                                "notif_subject_deadline_demande_documents",
                                Map.of("numero", dossier.getNumber())))
                        .content(portalConfigService.resolveNotificationText(
                                "notif_content_deadline_demande_documents",
                                Map.of("numero", dossier.getNumber())))
                        .scheduledAt(Instant.now())
                        .build();

                notificationRepository.save(alert);
                log.warn("[Notification] Alerte demande de documents créée — dossier: {}",
                        dossier.getNumber());
            }
        }

        List<gov.bf.ascelc.univers_audits.model.entity.DemandeDocuments> dueDemandeDocuments =
                demandeDocumentsRepository.findDueWithin(now, in3Days);

        for (var demande : dueDemandeDocuments) {
            gov.bf.ascelc.univers_audits.model.entity.Dossier dossier =
                    demande.getInvestigation().getDossier();
            boolean alreadyAlerted = notificationRepository
                    .existsByDemandeDocumentsIdAndTypeAndCreatedAtAfter(
                            demande.getId(),
                            NotificationType.DEMANDE_DOCUMENTS_ALERT_J3,
                            demande.getSentAt());

            if (!alreadyAlerted) {
                Notification alert = Notification.builder()
                        .dossier(dossier)
                        .demandeDocuments(demande)
                        .type(NotificationType.DEMANDE_DOCUMENTS_ALERT_J3)
                        .channel(NotificationChannel.PORTAL)
                        .subject(portalConfigService.resolveNotificationText(
                                "notif_subject_deadline_demande_documents_j3",
                                Map.of("numero", dossier.getNumber())))
                        .content(portalConfigService.resolveNotificationText(
                                "notif_content_deadline_demande_documents_j3",
                                Map.of("numero", dossier.getNumber())))
                        .scheduledAt(Instant.now())
                        .build();

                notificationRepository.save(alert);
                log.warn("[Notification] Alerte demande de documents J-3 créée — dossier: {}",
                        dossier.getNumber());
            }
        }

        log.info("[Notification] Alertes traitées — {} AR, {} compléments, {} investigations, "
                        + "{} AR J-3, {} compléments J-3, {} investigations J-3, "
                        + "{} demandes documents, {} demandes documents J-3",
                overdueAcknowledgments.size(),
                overdueComplements.size(),
                overdueInvestigations.size(),
                dueAcknowledgments.size(),
                dueComplements.size(),
                dueInvestigations.size(),
                overdueDemandeDocuments.size(),
                dueDemandeDocuments.size());
    }



    @Override
    @Scheduled(cron = "0 30 8 * * MON-FRI")
    @Transactional
    public void escaladeVersSuperieurs() {
        log.info("[Notification] Escalade automatique vers CGEA/CGE...");

        int delaiGraceJours = parametreDelaiService.resolveDelaiJours("ESCALADE_DELAI_GRACE");
        Instant graceThreshold = deadlineCalculator.addCalendarDays(Instant.now(), -delaiGraceJours);

        List<Agent> superieurs = resolveSuperieurs();
        if (superieurs.isEmpty()) {
            log.warn("[Notification] Aucun agent CGEA/CGE résolu — escalade ignorée pour ce passage");
            return;
        }

        int escaladesAR = escaladeDossiers(
                dossierRepository.findAcknowledgmentsOverdueBeyondGrace(graceThreshold),
                NotificationType.ESCALADE_AR,
                "notif_subject_escalade_ar", "notif_content_escalade_ar", superieurs);

        int escaladesComplement = escaladeDossiers(
                dossierRepository.findComplementsOverdueBeyondGrace(graceThreshold),
                NotificationType.ESCALADE_COMPLEMENT,
                "notif_subject_escalade_complement", "notif_content_escalade_complement", superieurs);

        List<Dossier> dossiersInvestigation = investigationRepository
                .findOverdueBeyondGrace(graceThreshold).stream()
                .map(Investigation::getDossier)
                .toList();
        int escaladesInvestigation = escaladeDossiers(
                dossiersInvestigation,
                NotificationType.ESCALADE_INVESTIGATION,
                "notif_subject_escalade_investigation", "notif_content_escalade_investigation", superieurs);

        log.info("[Notification] Escalades traitées — {} AR, {} compléments, {} investigations",
                escaladesAR, escaladesComplement, escaladesInvestigation);
    }

    private List<Agent> resolveSuperieurs() {
        Set<String> keycloakIds = new LinkedHashSet<>();
        keycloakIds.addAll(keycloakAdminService.getUserIdsByRole("CGEA"));
        keycloakIds.addAll(keycloakAdminService.getUserIdsByRole("CGE"));

        List<Agent> superieurs = new ArrayList<>();
        for (String keycloakId : keycloakIds) {
            agentRepository.findByKeycloakId(keycloakId)
                    .filter(Agent::getActif)
                    .ifPresent(superieurs::add);
        }
        return superieurs;
    }

    private int escaladeDossiers(List<Dossier> dossiers, NotificationType type,
                                  String subjectKey, String contentKey, List<Agent> superieurs) {
        int count = 0;
        for (Dossier dossier : dossiers) {
            boolean dejaEscalade = notificationRepository
                    .existsByDossierIdAndType(dossier.getId(), type);
            if (!dejaEscalade) {
                creerEscalades(dossier, null, type, subjectKey, contentKey, superieurs);
                count++;
            }
        }
        return count;
    }

    private void creerEscalades(Dossier dossier, DemandeDocuments demande,
                                 NotificationType type, String subjectKey, String contentKey,
                                 List<Agent> superieurs) {
        Map<String, String> placeholders = Map.of(
                "numero", dossier.getNumber(),
                "agentEnCharge", nomAgentEnCharge(dossier));

        String subject = portalConfigService.resolveNotificationText(subjectKey, placeholders);
        String content = portalConfigService.resolveNotificationText(contentKey, placeholders);

        for (Agent superieur : superieurs) {
            Notification escalade = Notification.builder()
                    .dossier(dossier)
                    .demandeDocuments(demande)
                    .type(type)
                    .channel(NotificationChannel.PORTAL)
                    .recipient(superieur.getKeycloakId())
                    .subject(subject)
                    .content(content)
                    .scheduledAt(Instant.now())
                    .build();
            notificationRepository.save(escalade);
        }
        log.warn("[Notification] Escalade {} créée — dossier: {}", type, dossier.getNumber());
    }

    private String nomAgentEnCharge(Dossier dossier) {
        Agent agent = dossier.getAgentInCharge();
        return agent != null ? agent.getNomComplet() : "agent non identifié";
    }


    private void doSend(Notification notif) {
        try {
            switch (notif.getChannel()) {
                case EMAIL       -> sendViaEmail(notif);
                case SMS         -> sendViaSms(notif);
                case POSTAL_MAIL -> logPostalMail(notif);
                case PORTAL      -> markAsPortalVisible(notif);
            }
            notif.markAsSent();
            log.info("[Notification] Envoyée — id: {}, canal: {}",
                    notif.getId(), notif.getChannel());
        } catch (Exception e) {
            notif.markAsFailed(e.getMessage());
            log.error("[Notification] Échec — id: {}, erreur: {}",
                    notif.getId(), e.getMessage());
        }
    }

    private void sendViaEmail(Notification notif) {
        if (notif.getRecipient() == null || notif.getRecipient().isBlank()) {
            throw new BusinessException("Adresse email manquante");
        }

        log.info("[Email] Envoi → {} : {}", notif.getRecipient(), notif.getSubject());
    }

    private void sendViaSms(Notification notif) {
        if (notif.getRecipient() == null || notif.getRecipient().isBlank()) {
            throw new BusinessException("Numéro de téléphone manquant");
        }
        log.info("[SMS] Envoi → {} : {}", notif.getRecipient(), notif.getContent());
    }

    private void logPostalMail(Notification notif) {
        log.info("[Courrier] À préparer — destinataire: {}, sujet: {}",
                notif.getRecipient(), notif.getSubject());
    }

    private void markAsPortalVisible(Notification notif) {
        log.info("[Portail] Notification disponible — sujet: {}", notif.getSubject());
    }


    private Notification getOrThrow(UUID id) {
        return notificationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Notification introuvable : " + id));
    }
}