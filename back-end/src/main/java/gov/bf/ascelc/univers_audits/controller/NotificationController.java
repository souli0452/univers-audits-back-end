package gov.bf.ascelc.univers_audits.controller;

import gov.bf.ascelc.univers_audits.enums.NotificationStatus;
import gov.bf.ascelc.univers_audits.mapper.DossierDetailsMapper;
import gov.bf.ascelc.univers_audits.model.dto.response.NotificationResponse;
import gov.bf.ascelc.univers_audits.model.entity.Agent;
import gov.bf.ascelc.univers_audits.repository.AgentRepository;
import gov.bf.ascelc.univers_audits.repository.NotificationRepository;
import gov.bf.ascelc.univers_audits.service.NotificationService;
import gov.bf.ascelc.univers_audits.shared.exceptions.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService      notificationService;
    private final NotificationRepository   notificationRepository;
    private final AgentRepository          agentRepository;
    private final DossierDetailsMapper     detailsMapper;

    // ── Mes notifications (topbar + page notifications) ───────
    @GetMapping("/my")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Page<NotificationResponse>> getMyNotifications(
            @RequestParam(defaultValue = "0")    int page,
            @RequestParam(defaultValue = "20")   int size,
            @RequestParam(defaultValue = "false") boolean unreadOnly,
            @AuthenticationPrincipal Jwt jwt) {

        Pageable pageable = PageRequest.of(page, size,
                Sort.by("createdAt").descending());

        // Trouver l'agent connecté
        Agent agent = agentRepository.findByKeycloakId(jwt.getSubject())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Agent introuvable"));

        Page<NotificationResponse> result;

        if (unreadOnly) {
            result = notificationRepository
                    .findByDossierAgentInChargeIdAndStatusIn(
                            agent.getId(),
                            List.of(NotificationStatus.PENDING,
                                    NotificationStatus.FAILED),
                            pageable)
                    .map(detailsMapper::toResponse);
        } else {
            result = notificationRepository
                    .findByDossierAgentInChargeId(agent.getId(), pageable)
                    .map(detailsMapper::toResponse);
        }

        return ResponseEntity.ok(result);
    }

    // ── Marquer une notification comme lue ────────────────────
    @PatchMapping("/{id}/read")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> markAsRead(@PathVariable UUID id) {
        notificationRepository.findById(id).ifPresent(n -> {
            // On ne change pas le statut SENT → on laisse tel quel
            // mais on pourrait ajouter un champ readAt si besoin
            notificationRepository.save(n);
        });
        return ResponseEntity.noContent().build();
    }

    // ── Marquer toutes comme lues ─────────────────────────────
    @PatchMapping("/read-all")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> markAllAsRead() {
        // Endpoint présent pour la cohérence frontend/backend
        // Implémentation complète possible quand le champ "read"
        // sera ajouté à l'entité Notification
        return ResponseEntity.noContent().build();
    }

    // ── Notifications d'un dossier ────────────────────────────
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

    // ── En attente ────────────────────────────────────────────
    @GetMapping("/pending")
    @PreAuthorize("hasAnyRole('ADMIN_DDIC','CGEA')")
    public ResponseEntity<Page<NotificationResponse>> getPending(
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {

        Pageable pageable = PageRequest.of(page, size,
                Sort.by("scheduledAt").ascending());
        return ResponseEntity.ok(
                notificationRepository
                        .findByStatusIn(
                                List.of(NotificationStatus.PENDING),
                                pageable)
                        .map(detailsMapper::toResponse));
    }

    // ── Envoi manuel ──────────────────────────────────────────
    @PatchMapping("/{id}/send")
    @PreAuthorize("hasAnyRole('ADMIN_DDIC','CGEA')")
    public ResponseEntity<NotificationResponse> sendNow(
            @PathVariable UUID id) {
        return ResponseEntity.ok(notificationService.sendNow(id));
    }

    // ── Annulation ────────────────────────────────────────────
    @PatchMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('ADMIN_DDIC','CGEA')")
    public ResponseEntity<NotificationResponse> cancel(
            @PathVariable UUID id,
            @RequestParam String reason) {
        return ResponseEntity.ok(notificationService.cancel(id, reason));
    }

    // ── Relance ───────────────────────────────────────────────
    @PatchMapping("/{id}/retry")
    @PreAuthorize("hasAnyRole('ADMIN_DDIC','CGEA')")
    public ResponseEntity<NotificationResponse> retry(
            @PathVariable UUID id) {
        return ResponseEntity.ok(notificationService.retry(id));
    }
}