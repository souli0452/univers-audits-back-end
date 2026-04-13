package gov.bf.ascelc.univers_audits.controller;

import gov.bf.ascelc.univers_audits.model.dto.request.AddMemberRequest;
import gov.bf.ascelc.univers_audits.model.dto.request.ExtendDeadlineRequest;
import gov.bf.ascelc.univers_audits.model.dto.request.InvestigationCreateRequest;
import gov.bf.ascelc.univers_audits.model.dto.request.InvestigationUpdateRequest;
import gov.bf.ascelc.univers_audits.model.dto.response.InvestigationResponse;
import gov.bf.ascelc.univers_audits.service.InvestigationService;
import gov.bf.ascelc.univers_audits.shared.utils.ApiUrls;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 *
 *  BASE URL : /api/v1/investigations
 *
 *  CYCLE DE VIE (90 jours Manuel B) :
 *  POST /dossier/{id}/open     → CGEA ouvre l'investigation
 *  PATCH /{id}/start           → Démarrage officiel (chrono 90j)
 *  PATCH /{id}/suspend         → Suspension temporaire
 *  PATCH /{id}/resume          → Reprise
 *  PATCH /{id}/extend-deadline → Extension délai (CGEA)
 *  PATCH /{id}/submit-report   → Rapport final soumis
 *  PATCH /{id}/approve-dei     → Approbation DEI (15j)
 *  PATCH /{id}/approve-legal   → Approbation Conseiller (10j)
 *  PATCH /{id}/approve-cge     → Décision finale CGE (20j)
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping(ApiUrls.INVESTIGATIONS)
public class InvestigationController {

    private final InvestigationService investigationService;

    @GetMapping
    @PreAuthorize("hasAnyRole('CGEA', 'CGE', 'CONTROLEUR_ETAT',"
            + "'ADMIN_DDIC')")
    public ResponseEntity<Page<InvestigationResponse>> findAll(
            @PageableDefault(size = 20, sort = "createdAt")
            Pageable pageable) {
        return ResponseEntity.ok(
                investigationService.findAll(pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('CGEA', 'CGE', 'CONTROLEUR_ETAT',"
            + "'MEMBRE_CTADP', 'ADMIN_DDIC')")
    public ResponseEntity<InvestigationResponse> findById(
            @PathVariable UUID id) {
        return ResponseEntity.ok(
                investigationService.findById(id));
    }

    @GetMapping("/dossier/{dossierId}")
    @PreAuthorize("hasAnyRole('CGEA', 'CGE', 'CONTROLEUR_ETAT',"
            + "'MEMBRE_CTADP', 'ADMIN_DDIC')")
    public ResponseEntity<InvestigationResponse> findByDossierId(
            @PathVariable UUID dossierId) {
        return ResponseEntity.ok(
                investigationService.findByDossierId(dossierId));
    }

    @GetMapping("/overdue")
    @PreAuthorize("hasAnyRole('CGEA', 'CGE', 'ADMIN_DDIC')")
    public ResponseEntity<Page<InvestigationResponse>> findOverdue(
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(
                investigationService.findOverdue(pageable));
    }

    @PostMapping("/dossier/{dossierId}/open")
    @PreAuthorize("hasAnyRole('CGEA', 'ADMIN_DDIC')")
    public ResponseEntity<InvestigationResponse> open(
            @PathVariable UUID dossierId,
            @Valid @RequestBody InvestigationCreateRequest request,
            HttpServletRequest httpRequest) {
        log.info("Ouverture investigation — dossier: {}",
                dossierId);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(investigationService.open(
                        dossierId, request,
                        getClientIp(httpRequest)));
    }

    @PatchMapping("/{id}/start")
    @PreAuthorize("hasAnyRole('CGEA', 'ADMIN_DDIC')")
    public ResponseEntity<InvestigationResponse> start(
            @PathVariable UUID id,
            HttpServletRequest httpRequest) {
        log.info("Démarrage officiel investigation {}", id);
        return ResponseEntity.ok(
                investigationService.start(
                        id, getClientIp(httpRequest)));
    }


    @PatchMapping("/{id}/suspend")
    @PreAuthorize("hasAnyRole('CGEA', 'CGE', 'ADMIN_DDIC')")
    public ResponseEntity<InvestigationResponse> suspend(
            @PathVariable UUID id,
            @RequestParam String reason,
            HttpServletRequest httpRequest) {
        log.info("Suspension investigation {} — motif: {}",
                id, reason);
        return ResponseEntity.ok(
                investigationService.suspend(
                        id, reason, getClientIp(httpRequest)));
    }

    @PatchMapping("/{id}/resume")
    @PreAuthorize("hasAnyRole('CGEA', 'CGE', 'ADMIN_DDIC')")
    public ResponseEntity<InvestigationResponse> resume(
            @PathVariable UUID id,
            @RequestParam(required = false) String reason,
            HttpServletRequest httpRequest) {
        log.info("Reprise investigation {}", id);
        return ResponseEntity.ok(
                investigationService.resume(
                        id, reason, getClientIp(httpRequest)));
    }

    @PatchMapping("/{id}/extend-deadline")
    @PreAuthorize("hasAnyRole('CGEA', 'ADMIN_DDIC')")
    public ResponseEntity<InvestigationResponse> extendDeadline(
            @PathVariable UUID id,
            @Valid @RequestBody ExtendDeadlineRequest request,
            HttpServletRequest httpRequest) {
        log.info("Extension délai investigation {} → {}",
                id, request.getNewDeadline());
        return ResponseEntity.ok(
                investigationService.extendDeadline(
                        id, request, getClientIp(httpRequest)));
    }

    @PatchMapping("/{id}/submit-report")
    @PreAuthorize("hasAnyRole('CONTROLEUR_ETAT', 'ADMIN_DDIC')")
    public ResponseEntity<InvestigationResponse> submitReport(
            @PathVariable UUID id,
            @Valid @RequestBody InvestigationUpdateRequest request,
            HttpServletRequest httpRequest) {
        log.info("Rapport final soumis — investigation {}", id);
        return ResponseEntity.ok(
                investigationService.submitReport(
                        id, request, getClientIp(httpRequest)));
    }

    @PatchMapping("/{id}/approve-dei")
    @PreAuthorize("hasAnyRole('CGEA', 'ADMIN_DDIC')")
    public ResponseEntity<InvestigationResponse> approveDei(
            @PathVariable UUID id,
            HttpServletRequest httpRequest) {
        log.info("Approbation DEI — investigation {}", id);
        return ResponseEntity.ok(
                investigationService.approveDei(
                        id, getClientIp(httpRequest)));
    }

    @PatchMapping("/{id}/approve-legal")
    @PreAuthorize("hasAnyRole('CONSEILLER_JURIDIQUE', 'ADMIN_DDIC')")
    public ResponseEntity<InvestigationResponse> approveLegalAdvisor(
            @PathVariable UUID id,
            HttpServletRequest httpRequest) {
        log.info("Approbation conseiller juridique — investigation {}",
                id);
        return ResponseEntity.ok(
                investigationService.approveLegalAdvisor(
                        id, getClientIp(httpRequest)));
    }

    @PatchMapping("/{id}/approve-cge")
    @PreAuthorize("hasAnyRole('CGE', 'ADMIN_DDIC')")
    public ResponseEntity<InvestigationResponse> approveCge(
            @PathVariable UUID id,
            @RequestParam String reason,
            HttpServletRequest httpRequest) {
        log.info("Décision finale CGE — investigation {}", id);
        return ResponseEntity.ok(
                investigationService.approveCge(
                        id, reason, getClientIp(httpRequest)));
    }

    @PostMapping("/{id}/members")
    @PreAuthorize("hasAnyRole('CGEA', 'ADMIN_DDIC')")
    public ResponseEntity<InvestigationResponse> addMember(
            @PathVariable UUID id,
            @Valid @RequestBody AddMemberRequest request,
            HttpServletRequest httpRequest) {
        log.info("Ajout membre à l'investigation {} — agent: {}",
                id, request.getAgentId());
        return ResponseEntity.ok(
                investigationService.addMember(
                        id, request, getClientIp(httpRequest)));
    }

    @DeleteMapping("/{id}/members/{agentId}")
    @PreAuthorize("hasAnyRole('CGEA', 'ADMIN_DDIC')")
    public ResponseEntity<InvestigationResponse> removeMember(
            @PathVariable UUID id,
            @PathVariable UUID agentId,
            HttpServletRequest httpRequest) {
        log.info("Retrait membre {} de l'investigation {}",
                agentId, id);
        return ResponseEntity.ok(
                investigationService.removeMember(
                        id, agentId, getClientIp(httpRequest)));
    }

    private String getClientIp(HttpServletRequest request) {
        String forwardedFor =
                request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}