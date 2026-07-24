package gov.bf.ascelc.univers_audits.controller;

import gov.bf.ascelc.univers_audits.model.dto.request.AddMemberRequest;
import gov.bf.ascelc.univers_audits.model.dto.request.ExtendDeadlineRequest;
import gov.bf.ascelc.univers_audits.model.dto.request.InvestigationCreateRequest;
import gov.bf.ascelc.univers_audits.model.dto.request.InvestigationUpdateRequest;
import gov.bf.ascelc.univers_audits.model.dto.response.InvestigationResponse;
import gov.bf.ascelc.univers_audits.service.AuditService;
import gov.bf.ascelc.univers_audits.service.InvestigationService;
import gov.bf.ascelc.univers_audits.shared.exceptions.ResourceNotFoundException;
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
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping(ApiUrls.INVESTIGATIONS)
public class InvestigationController {

    private final InvestigationService investigationService;
    private final AuditService         auditService;

    private String getClientIp(HttpServletRequest req) {
        String xff = req.getHeader("X-Forwarded-For");
        return (xff != null && !xff.isBlank())
                ? xff.split(",")[0].trim()
                : req.getRemoteAddr();
    }

    private String id(Jwt jwt)   { return jwt != null ? jwt.getSubject() : "SYSTEM"; }
    private String name(Jwt jwt) { return jwt != null ? jwt.getClaimAsString("name") : null; }
    private String role(Jwt jwt) {
        if (jwt == null) return null;
        var r = jwt.getClaimAsStringList("roles");
        return (r != null && !r.isEmpty()) ? r.get(0) : null;
    }

    // ── Lecture ───────────────────────────────────────────────

    @GetMapping
    @PreAuthorize("hasAnyRole('CGEA','CGE','CONTROLEUR_ETAT','ADMIN_DDIC')")
    public ResponseEntity<Page<InvestigationResponse>> findAll(
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        return ResponseEntity.ok(investigationService.findAll(pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('CGEA','CGE','CONTROLEUR_ETAT','MEMBRE_CTADP','ADMIN_DDIC')")
    public ResponseEntity<InvestigationResponse> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(investigationService.findById(id));
    }

    @GetMapping("/dossier/{dossierId}")
    @PreAuthorize("hasAnyRole('CGEA','CGE','CONTROLEUR_ETAT','MEMBRE_CTADP','ADMIN_DDIC')")
    public ResponseEntity<InvestigationResponse> findByDossierId(
            @PathVariable UUID dossierId) {
        try {
            return ResponseEntity.ok(investigationService.findByDossierId(dossierId));
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.noContent().build();
        }
    }

    @GetMapping("/overdue")
    @PreAuthorize("hasAnyRole('CGEA','CGE','ADMIN_DDIC')")
    public ResponseEntity<Page<InvestigationResponse>> findOverdue(
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(investigationService.findOverdue(pageable));
    }

    /**
     * GET /investigations/by-period?start=...&end=...
     * Utilisé par le rapport d'état des investigations.
     * AGENT_BRPD exclu — conforme Manuel §A.2.1
     */
    @GetMapping("/by-period")
    @PreAuthorize("hasAnyRole('CGE','CGEA','CONTROLEUR_ETAT',"
            + "'CONSEILLER_JURIDIQUE','MEMBRE_CTADP','ADMIN_DDIC')")
    public ResponseEntity<Page<InvestigationResponse>> findByPeriod(
            @RequestParam String start,
            @RequestParam String end,
            @PageableDefault(size = 1000, sort = "startDate") Pageable pageable) {

        Instant s = Instant.parse(start);
        Instant e = Instant.parse(end);
        log.info("[Rapport] Investigations par période {} → {}", start, end);
        return ResponseEntity.ok(
                investigationService.findByPeriod(s, e, pageable));
    }

    // ── Workflow ──────────────────────────────────────────────

    @PostMapping("/dossier/{dossierId}/open")
    @PreAuthorize("hasAnyRole('CGEA','ADMIN_DDIC')")
    public ResponseEntity<InvestigationResponse> open(
            @PathVariable UUID dossierId,
            @Valid @RequestBody InvestigationCreateRequest request,
            HttpServletRequest httpRequest,
            @AuthenticationPrincipal Jwt jwt) {

        log.info("Ouverture investigation — dossier: {}", dossierId);
        InvestigationResponse result = investigationService.open(
                dossierId, request, getClientIp(httpRequest));

        auditService.logAction(
                id(jwt), name(jwt), role(jwt),
                "OUVRIR_INVESTIGATION", "INVESTIGATION", result.getId().toString(),
                "Ouverture investigation pour dossier " + dossierId,
                AuditService.extractIp(httpRequest), AuditService.extractUserAgent(httpRequest));

        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    @PatchMapping("/{id}/start")
    @PreAuthorize("hasAnyRole('CGEA','ADMIN_DDIC')")
    public ResponseEntity<InvestigationResponse> start(
            @PathVariable UUID id,
            HttpServletRequest httpRequest,
            @AuthenticationPrincipal Jwt jwt) {

        log.info("Démarrage officiel investigation {}", id);
        InvestigationResponse result = investigationService.start(
                id, getClientIp(httpRequest));

        auditService.logAction(
                id(jwt), name(jwt), role(jwt),
                "DEMARRER_INVESTIGATION", "INVESTIGATION", id.toString(),
                "Démarrage officiel de l'investigation", AuditService.extractIp(httpRequest), AuditService.extractUserAgent(httpRequest));

        return ResponseEntity.ok(result);
    }

    @PatchMapping("/{id}/suspend")
    @PreAuthorize("hasAnyRole('CGEA','CGE','ADMIN_DDIC')")
    public ResponseEntity<InvestigationResponse> suspend(
            @PathVariable UUID id,
            @RequestParam String reason,
            HttpServletRequest httpRequest,
            @AuthenticationPrincipal Jwt jwt) {

        log.info("Suspension investigation {} — motif: {}", id, reason);
        InvestigationResponse result = investigationService.suspend(
                id, reason, getClientIp(httpRequest));

        auditService.logAction(
                id(jwt), name(jwt), role(jwt),
                "SUSPENDRE_INVESTIGATION", "INVESTIGATION", id.toString(),
                "Suspension — motif : " + reason, AuditService.extractIp(httpRequest), AuditService.extractUserAgent(httpRequest));

        return ResponseEntity.ok(result);
    }

    @PatchMapping("/{id}/resume")
    @PreAuthorize("hasAnyRole('CGEA','CGE','ADMIN_DDIC')")
    public ResponseEntity<InvestigationResponse> resume(
            @PathVariable UUID id,
            @RequestParam(required = false) String reason,
            HttpServletRequest httpRequest,
            @AuthenticationPrincipal Jwt jwt) {

        log.info("Reprise investigation {}", id);
        InvestigationResponse result = investigationService.resume(
                id, reason, getClientIp(httpRequest));

        auditService.logAction(
                id(jwt), name(jwt), role(jwt),
                "REPRENDRE_INVESTIGATION", "INVESTIGATION", id.toString(),
                "Reprise" + (reason != null ? " — " + reason : ""),
                AuditService.extractIp(httpRequest), AuditService.extractUserAgent(httpRequest));

        return ResponseEntity.ok(result);
    }

    @PatchMapping("/{id}/extend-deadline")
    @PreAuthorize("hasAnyRole('CGEA','ADMIN_DDIC')")
    public ResponseEntity<InvestigationResponse> extendDeadline(
            @PathVariable UUID id,
            @Valid @RequestBody ExtendDeadlineRequest request,
            HttpServletRequest httpRequest,
            @AuthenticationPrincipal Jwt jwt) {

        log.info("Extension délai investigation {} → {}", id, request.getNewDeadline());
        InvestigationResponse result = investigationService.extendDeadline(
                id, request, getClientIp(httpRequest));

        auditService.logAction(
                id(jwt), name(jwt), role(jwt),
                "PROLONGER_INVESTIGATION", "INVESTIGATION", id.toString(),
                "Prolongation délai → " + request.getNewDeadline(), AuditService.extractIp(httpRequest), AuditService.extractUserAgent(httpRequest));

        return ResponseEntity.ok(result);
    }

    @PatchMapping("/{id}/submit-report")
    @PreAuthorize("hasAnyRole('CONTROLEUR_ETAT','ADMIN_DDIC')")
    public ResponseEntity<InvestigationResponse> submitReport(
            @PathVariable UUID id,
            @Valid @RequestBody InvestigationUpdateRequest request,
            HttpServletRequest httpRequest,
            @AuthenticationPrincipal Jwt jwt) {

        log.info("Rapport final soumis — investigation {}", id);
        InvestigationResponse result = investigationService.submitReport(
                id, request, getClientIp(httpRequest));

        auditService.logAction(
                id(jwt), name(jwt), role(jwt),
                "SOUMETTRE_RAPPORT", "INVESTIGATION", id.toString(),
                "Soumission du rapport final", AuditService.extractIp(httpRequest), AuditService.extractUserAgent(httpRequest));

        return ResponseEntity.ok(result);
    }

    @PatchMapping("/{id}/approve-dei")
    @PreAuthorize("hasAnyRole('CGEA','ADMIN_DDIC')")
    public ResponseEntity<InvestigationResponse> approveDei(
            @PathVariable UUID id,
            HttpServletRequest httpRequest,
            @AuthenticationPrincipal Jwt jwt) {

        log.info("Approbation DEI — investigation {}", id);
        InvestigationResponse result = investigationService.approveDei(
                id, getClientIp(httpRequest));

        auditService.logAction(
                id(jwt), name(jwt), role(jwt),
                "APPROUVER_DEI", "INVESTIGATION", id.toString(),
                "Approbation DEI", AuditService.extractIp(httpRequest), AuditService.extractUserAgent(httpRequest));

        return ResponseEntity.ok(result);
    }

    @PatchMapping("/{id}/approve-legal")
    @PreAuthorize("hasAnyRole('CONSEILLER_JURIDIQUE','ADMIN_DDIC')")
    public ResponseEntity<InvestigationResponse> approveLegalAdvisor(
            @PathVariable UUID id,
            HttpServletRequest httpRequest,
            @AuthenticationPrincipal Jwt jwt) {

        log.info("Approbation conseiller juridique — investigation {}", id);
        InvestigationResponse result = investigationService.approveLegalAdvisor(
                id, getClientIp(httpRequest));

        auditService.logAction(
                id(jwt), name(jwt), role(jwt),
                "APPROUVER_JURIDIQUE", "INVESTIGATION", id.toString(),
                "Approbation conseiller juridique", AuditService.extractIp(httpRequest), AuditService.extractUserAgent(httpRequest));

        return ResponseEntity.ok(result);
    }

    @PatchMapping("/{id}/approve-cge")
    @PreAuthorize("hasAnyRole('CGE','ADMIN_DDIC')")
    public ResponseEntity<InvestigationResponse> approveCge(
            @PathVariable UUID id,
            @RequestParam String reason,
            HttpServletRequest httpRequest,
            @AuthenticationPrincipal Jwt jwt) {

        log.info("Décision finale CGE — investigation {}", id);
        InvestigationResponse result = investigationService.approveCge(
                id, reason, getClientIp(httpRequest));

        auditService.logAction(
                id(jwt), name(jwt), role(jwt),
                "APPROUVER_CGE", "INVESTIGATION", id.toString(),
                "Décision finale CGE — " + reason, AuditService.extractIp(httpRequest), AuditService.extractUserAgent(httpRequest));

        return ResponseEntity.ok(result);
    }

    // ── Équipe ────────────────────────────────────────────────

    @PostMapping("/{id}/members")
    @PreAuthorize("hasAnyRole('CGEA','ADMIN_DDIC')")
    public ResponseEntity<InvestigationResponse> addMember(
            @PathVariable UUID id,
            @Valid @RequestBody AddMemberRequest request,
            HttpServletRequest httpRequest,
            @AuthenticationPrincipal Jwt jwt) {

        log.info("Ajout membre à l'investigation {} — agent: {}", id, request.getAgentId());
        InvestigationResponse result = investigationService.addMember(
                id, request, getClientIp(httpRequest));

        auditService.logAction(
                id(jwt), name(jwt), role(jwt),
                "AJOUTER_MEMBRE", "INVESTIGATION", id.toString(),
                "Ajout membre agent " + request.getAgentId(), AuditService.extractIp(httpRequest), AuditService.extractUserAgent(httpRequest));

        return ResponseEntity.ok(result);
    }

    @DeleteMapping("/{id}/members/{agentId}")
    @PreAuthorize("hasAnyRole('CGEA','ADMIN_DDIC')")
    public ResponseEntity<InvestigationResponse> removeMember(
            @PathVariable UUID id,
            @PathVariable UUID agentId,
            HttpServletRequest httpRequest,
            @AuthenticationPrincipal Jwt jwt) {

        log.info("Retrait membre {} de l'investigation {}", agentId, id);
        InvestigationResponse result = investigationService.removeMember(
                id, agentId, getClientIp(httpRequest));

        auditService.logAction(
                id(jwt), name(jwt), role(jwt),
                "RETIRER_MEMBRE", "INVESTIGATION", id.toString(),
                "Retrait membre agent " + agentId, AuditService.extractIp(httpRequest), AuditService.extractUserAgent(httpRequest));

        return ResponseEntity.ok(result);
    }
}