package gov.bf.ascelc.univers_audits.controller;

import gov.bf.ascelc.univers_audits.model.dto.response.NotificationResponse;
import gov.bf.ascelc.univers_audits.security.permission.AppPermission;
import gov.bf.ascelc.univers_audits.security.permission.HasPermission;
import gov.bf.ascelc.univers_audits.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping("/my")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Page<NotificationResponse>> getMyNotifications(
            @RequestParam(defaultValue = "0")     int page,
            @RequestParam(defaultValue = "20")    int size,
            @RequestParam(defaultValue = "false") boolean unreadOnly,
            @AuthenticationPrincipal Jwt jwt) {

        Pageable pageable = PageRequest.of(page, size,
                Sort.by("createdAt").descending());

        return ResponseEntity.ok(notificationService.findMyNotifications(
                jwt.getSubject(), unreadOnly, pageable));
    }

    @GetMapping("/my/unread-count")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Long> getUnreadCount(
            @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(notificationService.countUnread(jwt.getSubject()));
    }

    @PatchMapping("/{id}/read")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> markAsRead(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt) {

        notificationService.markAsRead(id, jwt.getSubject());
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/read-all")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> markAllAsRead(
            @AuthenticationPrincipal Jwt jwt) {

        notificationService.markAllAsRead(jwt.getSubject());
        return ResponseEntity.noContent().build();
    }

    @HasPermission(AppPermission.NOTIFICATION_DOSSIER_VOIR)
    @GetMapping("/dossier/{dossierId}")
    @PreAuthorize("hasAnyRole('AGENT_BRPD','CONSEILLER_JURIDIQUE','MEMBRE_CTADP',"
            + "'CGEA','CGE','CONTROLEUR_ETAT','ADMIN_DDIC')")
    public ResponseEntity<Page<NotificationResponse>> getByDossier(
            @PathVariable UUID dossierId,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {

        Pageable pageable = PageRequest.of(page, size,
                Sort.by("createdAt").descending());
        return ResponseEntity.ok(
                notificationService.findByDossierId(dossierId, pageable));
    }

    @GetMapping("/pending")
    @HasPermission(AppPermission.NOTIFICATION_FILE_VOIR)
    public ResponseEntity<Page<NotificationResponse>> getPending(
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {

        Pageable pageable = PageRequest.of(page, size,
                Sort.by("scheduledAt").ascending());
        return ResponseEntity.ok(notificationService.findPending(pageable));
    }

    @PatchMapping("/{id}/send")
    @HasPermission(AppPermission.NOTIFICATION_FILE_GERER)
    public ResponseEntity<NotificationResponse> sendNow(@PathVariable UUID id) {
        return ResponseEntity.ok(notificationService.sendNow(id));
    }

    @PatchMapping("/{id}/cancel")
    @HasPermission(AppPermission.NOTIFICATION_FILE_GERER)
    public ResponseEntity<NotificationResponse> cancel(
            @PathVariable UUID id,
            @RequestParam String reason) {
        return ResponseEntity.ok(notificationService.cancel(id, reason));
    }

    @PatchMapping("/{id}/retry")
    @HasPermission(AppPermission.NOTIFICATION_FILE_GERER)
    public ResponseEntity<NotificationResponse> retry(@PathVariable UUID id) {
        return ResponseEntity.ok(notificationService.retry(id));
    }
}
