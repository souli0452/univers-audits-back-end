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
import gov.bf.ascelc.univers_audits.service.EmailService;
import gov.bf.ascelc.univers_audits.service.NotificationService;
import gov.bf.ascelc.univers_audits.service.SmsService;
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
    private final EmailService           emailService;
    private final SmsService             smsService;


    @Override
    public Page<NotificationResponse> findByDossierId(
            UUID dossierId, Pageable pageable) {
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


    @Transactional
    public void notifyDeclarantStatusChange(Dossier dossier,
                                            String status,
                                            String note) {
        if (dossier.getDeclarant() == null) return;

        String[] emailContent = EmailService.getStatusEmailContent(status);
        String statusLabel       = emailContent[0];
        String statusDescription = emailContent[1];

        String accessCode    = dossier.getAccessCode();
        String declarantName = isAnonymous(dossier)
                ? null
                : dossier.getDeclarant().getDisplayName();

        // ── Email ─────────────────────────────────────────────
        String email = dossier.getDeclarant().getEmail();
        if (email != null && !email.isBlank()) {
            emailService.sendStatusUpdate(
                    email,
                    accessCode,
                    declarantName,
                    statusLabel,
                    statusDescription,
                    note
            );

            saveNotification(dossier, NotificationType.STATUS_UPDATE,
                    NotificationChannel.EMAIL, email,
                    "Mise à jour de votre dossier — " + statusLabel,
                    statusDescription + (note != null ? "\n\nNote : " + note : "")
            );
        }

        String phone = dossier.getDeclarant().getPhoneNumber();
        if (phone != null && !phone.isBlank()) {
            smsService.sendStatusUpdate(phone, accessCode, statusLabel);

            saveNotification(dossier, NotificationType.STATUS_UPDATE,
                    NotificationChannel.SMS, phone,
                    "Mise à jour de votre dossier",
                    statusLabel
            );
        }

        log.info("[Notification] Déclarant notifié — statut: {} — dossier: {}",
                status, dossier.getId());
    }

    @Transactional
    public void notifyComplementRequest(Dossier dossier, String motif) {
        if (dossier.getDeclarant() == null) return;

        String accessCode    = dossier.getAccessCode();
        String declarantName = isAnonymous(dossier)
                ? null : dossier.getDeclarant().getDisplayName();

        String email = dossier.getDeclarant().getEmail();
        if (email != null && !email.isBlank()) {
            emailService.sendComplementRequest(
                    email, accessCode, declarantName, motif);

            saveNotification(dossier, NotificationType.COMPLEMENT_REQUEST,
                    NotificationChannel.EMAIL, email,
                    "Information complémentaire requise — votre dossier",
                    motif
            );
        }


        String phone = dossier.getDeclarant().getPhoneNumber();
        if (phone != null && !phone.isBlank()) {
            smsService.sendComplementRequest(phone, accessCode);

            saveNotification(dossier, NotificationType.COMPLEMENT_REQUEST,
                    NotificationChannel.SMS, phone,
                    "Complément requis",
                    "Action requise sur votre dossier"
            );
        }

        log.info("[Notification] Demande complément envoyée — dossier: {}",
                dossier.getId());
    }

    @Transactional
    public void notifyTransferExternal(Dossier dossier,
                                       String institutionLabel) {
        if (dossier.getDeclarant() == null) return;

        String accessCode    = dossier.getAccessCode();
        String declarantName = isAnonymous(dossier)
                ? null : dossier.getDeclarant().getDisplayName();

        String email = dossier.getDeclarant().getEmail();
        if (email != null && !email.isBlank()) {
            emailService.sendTransferExternal(
                    email, accessCode, declarantName, institutionLabel);

            saveNotification(dossier, NotificationType.STATUS_UPDATE,
                    NotificationChannel.EMAIL, email,
                    "Votre dossier a été transmis à une institution compétente",
                    "Transmis à : " + institutionLabel
            );
        }

        String phone = dossier.getDeclarant().getPhoneNumber();
        if (phone != null && !phone.isBlank()) {
            smsService.sendTransferExternal(phone, accessCode);

            saveNotification(dossier, NotificationType.STATUS_UPDATE,
                    NotificationChannel.SMS, phone,
                    "Dossier transmis",
                    "Votre dossier a été transmis"
            );
        }

        log.info("[Notification] Transfert notifié — dossier: {} → {}",
                dossier.getId(), institutionLabel);
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
                String alertTitle = "ALERTE : Délai AR dépassé";
                String alertBody  = String.format(
                        "Le délai légal de 7 jours pour l'envoi de l'accusé de " +
                                "réception B5 est dépassé pour le dossier %s. " +
                                "Action requise immédiatement.",
                        dossier.getNumber()
                );

                Notification alert = Notification.builder()
                        .dossier(dossier)
                        .type(NotificationType.DEADLINE_ALERT)
                        .channel(NotificationChannel.PORTAL)
                        .subject("ALERTE : Délai dépassé — " + dossier.getNumber())
                        .content(alertBody)
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
                        .subject("ALERTE : Complément non reçu — " + dossier.getNumber())
                        .content(String.format(
                                "Le délai de 14 jours pour recevoir le complément " +
                                        "d'information est dépassé pour le dossier %s.",
                                dossier.getNumber()
                        ))
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
                        .subject("ALERTE : Investigation dépassée — " + dossier.getNumber())
                        .content(String.format(
                                "L'investigation du dossier %s dépasse le délai " +
                                        "réglementaire de 90 jours. Une prolongation doit " +
                                        "être validée par le CGEA et le CGE.",
                                dossier.getNumber()
                        ))
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


    @Transactional
    private void saveNotification(Dossier dossier,
                                  NotificationType type,
                                  NotificationChannel channel,
                                  String recipient,
                                  String subject,
                                  String content) {
        Notification notif = Notification.builder()
                .dossier(dossier)
                .type(type)
                .channel(channel)
                .recipient(recipient)
                .subject(subject)
                .content(content)
                .scheduledAt(Instant.now())
                .build();

        notificationRepository.save(notif);
    }

    private boolean isAnonymous(Dossier dossier) {
        return dossier.getDeclarant() == null
                || Boolean.TRUE.equals(dossier.getDeclarant().isAnonymous())
                || Boolean.TRUE.equals(dossier.getDeclarant().getProtectionRequested());
    }

    private Notification getOrThrow(UUID id) {
        return notificationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Notification introuvable : " + id));
    }
}