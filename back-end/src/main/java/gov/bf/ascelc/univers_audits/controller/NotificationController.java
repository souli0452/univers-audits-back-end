package gov.bf.ascelc.univers_audits.controller;

import gov.bf.ascelc.univers_audits.enums.NotificationStatus;
import gov.bf.ascelc.univers_audits.model.dto.response.NotificationResponse;
import gov.bf.ascelc.univers_audits.model.entity.Agent;
import gov.bf.ascelc.univers_audits.model.entity.Notification;
import gov.bf.ascelc.univers_audits.repository.AgentRepository;
import gov.bf.ascelc.univers_audits.repository.NotificationRepository;
import gov.bf.ascelc.univers_audits.service.NotificationService;
import gov.bf.ascelc.univers_audits.shared.exceptions.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService    notificationService;
    private final NotificationRepository notificationRepository;
    private final AgentRepository        agentRepository;

    private Agent resolveAgent(Jwt jwt) {
        return agentRepository.findByKeycloakId(jwt.getSubject())
                .orElseThrow(() -> new ResourceNotFoundException("Agent introuvable"));
    }

    private NotificationResponse toResponse(Notification n) {
        return NotificationResponse.builder()
                .id(n.getId())
                .type(n.getType())
                .channel(n.getChannel())
                .subject(n.getSubject())
                .content(n.getContent())
                .status(n.getStatus())
                .formReference(n.getFormReference())
                .scheduledAt(n.getScheduledAt())
                .sentAt(n.getSentAt())
                .retryCount(n.getRetryCount())
                .readAt(n.getReadAt())
                .overdue(n.isOverdue())
                .dossierId(
                        n.getDossier() != null
                                ? n.getDossier().getId().toString()
                                : null)
                .dossierNumber(
                        n.getDossier() != null
                                ? n.getDossier().getNumber()
                                : null)
                .build();
    }

    @GetMapping("/my")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Page<NotificationResponse>> getMyNotifications(
            @RequestParam(defaultValue = "0")     int page,
            @RequestParam(defaultValue = "20")    int size,
            @RequestParam(defaultValue = "false") boolean unreadOnly,
            @AuthenticationPrincipal Jwt jwt) {

        Pageable pageable = PageRequest.of(page, size,
                Sort.by("createdAt").descending());

        Agent  agent      = resolveAgent(jwt);
        String keycloakId = jwt.getSubject();

        Page<NotificationResponse> result = unreadOnly
                ? notificationRepository
                .findUnreadByAgentOrRecipient(agent.getId(), keycloakId, pageable)
                .map(this::toResponse)
                : notificationRepository
                .findByAgentOrRecipient(agent.getId(), keycloakId, pageable)
                .map(this::toResponse);

        return ResponseEntity.ok(result);
    }

    @GetMapping("/my/unread-count")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Long> getUnreadCount(
            @AuthenticationPrincipal Jwt jwt) {

        Agent  agent      = resolveAgent(jwt);
        String keycloakId = jwt.getSubject();

        long count = notificationRepository
                .countUnreadByAgentOrRecipient(agent.getId(), keycloakId);

        return ResponseEntity.ok(count);
    }

    @PatchMapping("/{id}/read")
    @PreAuthorize("isAuthenticated()")
    @Transactional
    public ResponseEntity<Void> markAsRead(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt) {

        Agent  agent      = resolveAgent(jwt);
        String keycloakId = jwt.getSubject();

        Notification notification = notificationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Notification introuvable : " + id));

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
            return ResponseEntity.status(403).build();
        }

        notification.markAsRead();
        notificationRepository.save(notification);

        return ResponseEntity.noContent().build();
    }


    @PatchMapping("/read-all")
    @PreAuthorize("isAuthenticated()")
    @Transactional
    public ResponseEntity<Void> markAllAsRead(
            @AuthenticationPrincipal Jwt jwt) {

        Agent  agent      = resolveAgent(jwt);
        String keycloakId = jwt.getSubject();

        notificationRepository.markAllReadByAgentOrRecipient(
                agent.getId(), keycloakId, Instant.now());

        return ResponseEntity.noContent().build();
    }


    @GetMapping("/dossier/{dossierId}")
    @PreAuthorize("isAuthenticated()")
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
    @PreAuthorize("hasAnyRole('ADMIN_DDIC','CGEA')")
    public ResponseEntity<Page<NotificationResponse>> getPending(
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {

        Pageable pageable = PageRequest.of(page, size,
                Sort.by("scheduledAt").ascending());
        return ResponseEntity.ok(
                notificationRepository
                        .findByStatusIn(List.of(NotificationStatus.PENDING), pageable)
                        .map(this::toResponse));
    }

    @PatchMapping("/{id}/send")
    @PreAuthorize("hasAnyRole('ADMIN_DDIC','CGEA')")
    public ResponseEntity<NotificationResponse> sendNow(@PathVariable UUID id) {
        return ResponseEntity.ok(notificationService.sendNow(id));
    }

    @PatchMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('ADMIN_DDIC','CGEA')")
    public ResponseEntity<NotificationResponse> cancel(
            @PathVariable UUID id,
            @RequestParam String reason) {
        return ResponseEntity.ok(notificationService.cancel(id, reason));
    }

    @PatchMapping("/{id}/retry")
    @PreAuthorize("hasAnyRole('ADMIN_DDIC','CGEA')")
    public ResponseEntity<NotificationResponse> retry(@PathVariable UUID id) {
        return ResponseEntity.ok(notificationService.retry(id));
    }
}