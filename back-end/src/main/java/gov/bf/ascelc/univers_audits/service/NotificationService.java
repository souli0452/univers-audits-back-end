package gov.bf.ascelc.univers_audits.service;

import gov.bf.ascelc.univers_audits.model.dto.response.NotificationResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;


public interface NotificationService {

    Page<NotificationResponse> findByDossierId(
            UUID dossierId, Pageable pageable);

    List<NotificationResponse> findOverdue();

    List<NotificationResponse> findPending();

    // Envoie immédiatement une notification spécifique
    NotificationResponse sendNow(UUID notificationId);

    // Annule une notification planifiée avant son envoi
    NotificationResponse cancel(UUID notificationId,
                                String reason);

    // Relance une notification échouée (max 3 tentatives)
    NotificationResponse retry(UUID notificationId);

    // ── Envoi automatique (appelé par le scheduler) ───────────────

    // Traite toutes les notifications en attente dont la date
    // limite est dépassée — appelé toutes les 15 minutes
    void processPendingNotifications();

    // Envoie les alertes de délai pour les dossiers en retard
    void sendDeadlineAlerts();
}