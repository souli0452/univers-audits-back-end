package gov.bf.ascelc.univers_audits.controller;

import gov.bf.ascelc.univers_audits.model.dto.response.NotificationResponse;
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

/**
 * ═══════════════════════════════════════════════════════════════
 *  NotificationController — API REST des notifications légales
 * ═══════════════════════════════════════════════════════════════
 *
 *  BASE URL : /api/v1/notifications
 *
 *  Gère les notifications obligatoires du Manuel B :
 *  - Récépissé B4 (immédiat au guichet)
 *  - Accusé B5 signé CGE (dans les 7 jours)
 *  - Demande de complément (dans les 14 jours)
 *  - Réponse d'irrecevabilité (dans les 3 jours après CTADP)
 *  - Notification de transfert (dans les 7 jours)
 *  - Alertes de dépassement de délai
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping(ApiUrls.NOTIFICATIONS)
public class NotificationController {

    private final NotificationService notificationService;
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
}