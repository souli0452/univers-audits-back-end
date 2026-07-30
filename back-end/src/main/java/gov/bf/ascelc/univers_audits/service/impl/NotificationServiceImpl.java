package gov.bf.ascelc.univers_audits.service.impl;

import gov.bf.ascelc.univers_audits.enums.DossierStatus;
import gov.bf.ascelc.univers_audits.enums.NotificationChannel;
import gov.bf.ascelc.univers_audits.enums.NotificationStatus;
import gov.bf.ascelc.univers_audits.enums.NotificationType;
import gov.bf.ascelc.univers_audits.mapper.DossierDetailsMapper;
import gov.bf.ascelc.univers_audits.model.dto.response.NotificationResponse;
import gov.bf.ascelc.univers_audits.model.entity.Agent;
import gov.bf.ascelc.univers_audits.model.entity.Dossier;
import gov.bf.ascelc.univers_audits.model.entity.Notification;
import gov.bf.ascelc.univers_audits.repository.AgentRepository;
import gov.bf.ascelc.univers_audits.repository.DossierRepository;
import gov.bf.ascelc.univers_audits.repository.NotificationRepository;
import gov.bf.ascelc.univers_audits.service.NotificationService;
import gov.bf.ascelc.univers_audits.service.PortalConfigService;
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
import java.util.List;
import java.util.Map;
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
        log.info("[Notification] Envoi des alertes de délai dépassé...");

        List<Dossier> overdueAcknowledgments =
                dossierRepository.findOverdueAcknowledgments(Instant.now());

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
                dossierRepository.findOverdueComplementRequests(Instant.now());

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
                dossierRepository.findOverdueInvestigations(Instant.now());

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

        log.info("[Notification] Alertes traitées — {} AR, {} compléments, {} investigations",
                overdueAcknowledgments.size(),
                overdueComplements.size(),
                overdueInvestigations.size());
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