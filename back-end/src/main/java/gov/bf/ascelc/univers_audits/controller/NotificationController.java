package gov.bf.ascelc.univers_audits.controller;

import gov.bf.ascelc.univers_audits.enums.NotificationStatus;
import gov.bf.ascelc.univers_audits.model.dto.response.NotificationResponse;
import gov.bf.ascelc.univers_audits.model.entity.Notification;
import gov.bf.ascelc.univers_audits.repository.NotificationRepository;
import gov.bf.ascelc.univers_audits.service.NotificationService;
import gov.bf.ascelc.univers_audits.shared.utils.ApiUrls;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;


@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping(ApiUrls.NOTIFICATIONS)
public class NotificationController {

    private final NotificationService notificationService;
    private final NotificationRepository notificationRepository;

    @GetMapping("/dossier/{dossierId}")
    @PreAuthorize("hasAnyRole('AGENT_BRPD', 'CONSEILLER_JURIDIQUE',"
            + "'CGEA', 'CGE', 'ADMIN_DDIC')")
    public ResponseEntity<Page<NotificationResponse>> findByDossierId(
            @PathVariable UUID dossierId,
            @PageableDefault(size = 20, sort = "createdAt")
            Pageable pageable) {
        return ResponseEntity.ok(
                notificationService.findByDossierId(
                        dossierId, pageable));
    }

    @GetMapping("/overdue")
    @PreAuthorize("hasAnyRole('AGENT_BRPD', 'CGEA', 'CGE',"
            + "'ADMIN_DDIC')")
    public ResponseEntity<List<NotificationResponse>> findOverdue() {
        return ResponseEntity.ok(
                notificationService.findOverdue());
    }


    @GetMapping("/pending")
    @PreAuthorize("hasAnyRole('AGENT_BRPD', 'ADMIN_DDIC')")
    public ResponseEntity<List<NotificationResponse>> findPending() {
        return ResponseEntity.ok(
                notificationService.findPending());
    }


    @PatchMapping("/{id}/send")
    @PreAuthorize("hasAnyRole('AGENT_BRPD', 'CGE', 'ADMIN_DDIC')")
    public ResponseEntity<NotificationResponse> sendNow(
            @PathVariable UUID id) {
        log.info("Envoi manuel notification {}", id);
        return ResponseEntity.ok(
                notificationService.sendNow(id));
    }

    @PatchMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('AGENT_BRPD', 'ADMIN_DDIC')")
    public ResponseEntity<NotificationResponse> cancel(
            @PathVariable UUID id,
            @RequestParam String reason) {
        log.info("Annulation notification {} — motif: {}",
                id, reason);
        return ResponseEntity.ok(
                notificationService.cancel(id, reason));
    }

    @PatchMapping("/{id}/retry")
    @PreAuthorize("hasAnyRole('AGENT_BRPD', 'ADMIN_DDIC')")
    public ResponseEntity<NotificationResponse> retry(
            @PathVariable UUID id) {
        log.info("Relance notification {}", id);
        return ResponseEntity.ok(
                notificationService.retry(id));
    }

    @GetMapping("/my")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> getMyNotifications(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "false") boolean unreadOnly) {

        org.springframework.data.domain.PageRequest pageable =
                org.springframework.data.domain.PageRequest.of(
                        page, size,
                        org.springframework.data.domain.Sort
                                .by("createdAt").descending()
                );

        org.springframework.data.domain.Page<Notification> result =
                unreadOnly
                        ? notificationRepository.findByStatusIn(
                        java.util.List.of(NotificationStatus.PENDING),
                        pageable)
                        : notificationRepository.findAll(pageable);

        return ResponseEntity.ok(java.util.Map.of(
                "content", result.getContent().stream()
                        .map(n -> java.util.Map.of(
                                "id", n.getId().toString(),
                                "type", n.getType() != null
                                        ? n.getType().name() : "",
                                "subject", n.getSubject() != null
                                        ? n.getSubject() : "",
                                "content", n.getContent() != null
                                        ? n.getContent() : "",
                                "createdAt", n.getCreatedAt() != null
                                        ? n.getCreatedAt().toString() : "",
                                "status", n.getStatus() != null
                                        ? n.getStatus().name() : "",
                                "dossierId", n.getDossier() != null
                                        ? n.getDossier().getId().toString() : "",
                                "dossierNumber", n.getDossier() != null
                                        && n.getDossier().getNumber() != null
                                        ? n.getDossier().getNumber() : ""
                        ))
                        .toList(),
                "totalElements", result.getTotalElements(),
                "totalPages", result.getTotalPages()
        ));
    }
}