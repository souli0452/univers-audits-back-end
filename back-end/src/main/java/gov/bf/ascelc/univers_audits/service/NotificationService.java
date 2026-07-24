package gov.bf.ascelc.univers_audits.service;

import gov.bf.ascelc.univers_audits.model.dto.response.NotificationResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;


public interface NotificationService {

    Page<NotificationResponse> findByDossierId(
            UUID dossierId, Pageable pageable);

    Page<NotificationResponse> findMyNotifications(
            String keycloakId, boolean unreadOnly, Pageable pageable);

    long countUnread(String keycloakId);

    void markAsRead(UUID notificationId, String keycloakId);

    void markAllAsRead(String keycloakId);

    Page<NotificationResponse> findPending(Pageable pageable);


    List<NotificationResponse> findOverdue();

    List<NotificationResponse> findPending();

    NotificationResponse sendNow(UUID notificationId);

    NotificationResponse cancel(UUID notificationId,
                                String reason);


    NotificationResponse retry(UUID notificationId);


    void processPendingNotifications();


    void sendDeadlineAlerts();
}