package gov.bf.ascelc.univers_audits.service.impl;

import gov.bf.ascelc.univers_audits.enums.DossierStatus;
import gov.bf.ascelc.univers_audits.enums.NotificationChannel;
import gov.bf.ascelc.univers_audits.enums.NotificationStatus;
import gov.bf.ascelc.univers_audits.enums.NotificationType;
import gov.bf.ascelc.univers_audits.mapper.DossierDetailsMapper;
import gov.bf.ascelc.univers_audits.model.dto.response.NotificationResponse;
import gov.bf.ascelc.univers_audits.model.entity.Dossier;
import gov.bf.ascelc.univers_audits.model.entity.Notification;
import gov.bf.ascelc.univers_audits.repository.DossierRepository;
import gov.bf.ascelc.univers_audits.repository.NotificationRepository;
import gov.bf.ascelc.univers_audits.service.NotificationService;
import gov.bf.ascelc.univers_audits.shared.exceptions.BusinessException;
import gov.bf.ascelc.univers_audits.shared.exceptions.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository notificationRepository;
    private final DossierRepository      dossierRepository;
    private final DossierDetailsMapper   detailsMapper;

    // ── Lecture ───────────────────────────────────────────────

    @Override
    public Page<NotificationResponse> findByDossierId(
            UUID dossierId, Pageable pageable) {
        // Correction du bug : filtrage réel par dossierId
        return notificationRepository
                .findByDossierId(dossierId, pageable)
                .map(detailsMapper::toResponse);
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

    // ── Actions manuelles ─────────────────────────────────────

    @Override
    @Transactional
    public NotificationResponse sendNow(UUID notificationId) {
        Notification notif = getOrThrow(notificationId);

        if (notif.getStatus() == NotificationStatus.SENT) {
            throw new BusinessException(
                    "Cette notification a déjà été envoyée");
        }
        if (notif.getStatus() == NotificationStatus.CANCELLED) {
            throw new BusinessException(
                    "Cette notification a été annulée");
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

    // ── Tâches planifiées ─────────────────────────────────────

    /** Traitement des notifications en attente toutes les 15 minutes */
    @Override
    @Scheduled(fixedDelay = 900_000)
    @Transactional
    public void processPendingNotifications() {
        log.info("Traitement des notifications en attente...");

        List<Notification> overdueNotifs =
                notificationRepository.findOverdue(Instant.now());

        int sent = 0;
        int failed = 0;

        for (Notification notif : overdueNotifs) {
            try {
                doSend(notif);
                notificationRepository.save(notif);
                sent++;
            } catch (Exception e) {
                log.error("Échec envoi notification {} : {}",
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
                log.error("Échec relance notification {} : {}",
                        notif.getId(), e.getMessage());
                failed++;
            }
        }

        log.info("Notifications traitées — envoyées: {}, échouées: {}",
                sent, failed);
    }

    /** Alertes de délai dépassé chaque matin en semaine à 8h */
    @Override
    @Scheduled(cron = "0 0 8 * * MON-FRI")
    @Transactional
    public void sendDeadlineAlerts() {
        log.info("Envoi des alertes de délai dépassé...");

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
                        .subject("ALERTE : Délai dépassé — "
                                + dossier.getNumber())
                        .content("Le délai légal de 7 jours pour l'envoi "
                                + "de l'accusé de réception B5 est dépassé "
                                + "pour le dossier " + dossier.getNumber()
                                + ". Action requise immédiatement.")
                        .scheduledAt(Instant.now())
                        .build();

                notificationRepository.save(alert);
                log.warn("Alerte délai créée — dossier: {}",
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
                        .subject("ALERTE : Complément non reçu — "
                                + dossier.getNumber())
                        .content("Le délai de 14 jours pour recevoir "
                                + "le complément d'information est dépassé "
                                + "pour le dossier " + dossier.getNumber() + ".")
                        .scheduledAt(Instant.now())
                        .build();

                notificationRepository.save(alert);
                log.warn("Alerte complément créée — dossier: {}",
                        dossier.getNumber());
            }
        }

        log.info("Alertes délai traitées — {} AR en retard, {} compléments en retard",
                overdueAcknowledgments.size(), overdueComplements.size());
    }

    // ── Envoi effectif ────────────────────────────────────────

    private void doSend(Notification notif) {
        try {
            switch (notif.getChannel()) {
                case EMAIL       -> sendEmail(notif);
                case SMS         -> sendSms(notif);
                case POSTAL_MAIL -> logPostalMail(notif);
                case PORTAL      -> markAsPortalVisible(notif);
            }
            notif.markAsSent();
            log.info("Notification envoyée — id: {}, canal: {}",
                    notif.getId(), notif.getChannel());
        } catch (Exception e) {
            notif.markAsFailed(e.getMessage());
            log.error("Échec envoi — id: {}, erreur: {}",
                    notif.getId(), e.getMessage());
        }
    }

    private void sendEmail(Notification notif) {
        if (notif.getRecipient() == null || notif.getRecipient().isBlank()) {
            throw new BusinessException(
                    "Adresse email du destinataire manquante");
        }
        // TODO : intégrer JavaMailSender ou un service SMTP externe
        log.info("EMAIL simulé → {} : {}", notif.getRecipient(), notif.getSubject());
    }

    private void sendSms(Notification notif) {
        if (notif.getRecipient() == null || notif.getRecipient().isBlank()) {
            throw new BusinessException(
                    "Numéro de téléphone du destinataire manquant");
        }
        // TODO : intégrer un provider SMS (ex: Orange BF, Twilio)
        log.info("SMS simulé → {} : {}", notif.getRecipient(), notif.getContent());
    }

    private void logPostalMail(Notification notif) {
        log.info("Courrier postal à préparer — dossier: {}, destinataire: {}",
                notif.getDossier().getNumber(), notif.getRecipient());
    }

    private void markAsPortalVisible(Notification notif) {
        log.info("Notification portail disponible — dossier: {}",
                notif.getDossier().getNumber());
    }

    // ── Utilitaire ────────────────────────────────────────────

    private Notification getOrThrow(UUID id) {
        return notificationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Notification introuvable : " + id));
    }
}