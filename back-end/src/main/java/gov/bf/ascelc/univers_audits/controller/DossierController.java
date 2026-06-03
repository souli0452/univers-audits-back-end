package gov.bf.ascelc.univers_audits.controller;

import gov.bf.ascelc.univers_audits.enums.DossierStatus;
import gov.bf.ascelc.univers_audits.model.dto.request.DossierCreateRequest;
import gov.bf.ascelc.univers_audits.model.dto.request.DossierUpdateRequest;
import gov.bf.ascelc.univers_audits.model.dto.request.SetPriorityRequest;
import gov.bf.ascelc.univers_audits.model.dto.request.StatusTransitionRequest;
import gov.bf.ascelc.univers_audits.model.dto.response.DossierResponse;
import gov.bf.ascelc.univers_audits.service.AuditService;
import gov.bf.ascelc.univers_audits.service.DossierService;
import gov.bf.ascelc.univers_audits.shared.utils.ApiUrls;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping(ApiUrls.DOSSIERS)
public class DossierController {

    private final DossierService dossierService;
    private final AuditService   auditService;

    private String getClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        return (xff != null && !xff.isBlank())
                ? xff.split(",")[0].trim()
                : request.getRemoteAddr();
    }

    private String agentId(Jwt jwt)   { return jwt != null ? jwt.getSubject() : "ANONYMOUS"; }
    private String agentName(Jwt jwt) { return jwt != null ? jwt.getClaimAsString("name") : null; }
    private String agentRole(Jwt jwt) {
        if (jwt == null) return null;
        var roles = jwt.getClaimAsStringList("roles");
        return roles != null && !roles.isEmpty() ? roles.get(0) : null;
    }


    @GetMapping("/public/track/{accessCode}")
    public ResponseEntity<DossierResponse> trackByAccessCode(
            @PathVariable String accessCode) {
        log.info("Suivi public — accessCode: {}", accessCode);
        return ResponseEntity.ok(dossierService.findByAccessCode(accessCode));
    }

    @PostMapping("/public/submit")
    public ResponseEntity<DossierResponse> submitPublic(
            @Valid @RequestBody DossierCreateRequest request,
            HttpServletRequest httpRequest) {
        log.info("Soumission publique — mode: {}", request.getSubmissionMode());
        DossierResponse result = dossierService.submit(request, getClientIp(httpRequest));
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }


    @GetMapping
    @PreAuthorize("hasAnyRole('AGENT_BRPD','CONSEILLER_JURIDIQUE',"
            + "'MEMBRE_CTADP','CGEA','CGE','CONTROLEUR_ETAT','ADMIN_DDIC')")
    public ResponseEntity<Page<DossierResponse>> findAll(
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable,
            @RequestParam(required = false) String start,
            @RequestParam(required = false) String end) {

        if (start != null && end != null) {
            Instant s = Instant.parse(start);
            Instant e = Instant.parse(end);
            log.info("Dossiers par période {} → {}", start, end);
            return ResponseEntity.ok(
                    dossierService.findByReceptionDateBetween(s, e, pageable));
        }

        return ResponseEntity.ok(dossierService.findAll(pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('AGENT_BRPD','CONSEILLER_JURIDIQUE',"
            + "'MEMBRE_CTADP','CGEA','CGE','CONTROLEUR_ETAT','ADMIN_DDIC')")
    public ResponseEntity<DossierResponse> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(dossierService.findById(id));
    }

    @GetMapping("/status/{status}")
    @PreAuthorize("hasAnyRole('AGENT_BRPD','CONSEILLER_JURIDIQUE',"
            + "'MEMBRE_CTADP','CGEA','CGE','CONTROLEUR_ETAT','ADMIN_DDIC')")
    public ResponseEntity<Page<DossierResponse>> findByStatus(
            @PathVariable DossierStatus status,
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        return ResponseEntity.ok(dossierService.findByStatus(status, pageable));
    }

    @GetMapping("/my")
    @PreAuthorize("hasAnyRole('AGENT_BRPD','CONSEILLER_JURIDIQUE',"
            + "'CONTROLEUR_ETAT','CGEA')")
    public ResponseEntity<Page<DossierResponse>> findMyDossiers(
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        return ResponseEntity.ok(dossierService.findMyDossiers(pageable));
    }


    @PostMapping
    @PreAuthorize("hasAnyRole('AGENT_BRPD','ADMIN_DDIC')")
    public ResponseEntity<DossierResponse> create(
            @Valid @RequestBody DossierCreateRequest request,
            HttpServletRequest httpRequest,
            @AuthenticationPrincipal Jwt jwt) {

        log.info("Création dossier par agent — mode: {}", request.getSubmissionMode());
        DossierResponse result = dossierService.submit(request, getClientIp(httpRequest));

        auditService.logAction(
                agentId(jwt), agentName(jwt), agentRole(jwt),
                "CREER_DOSSIER", "DOSSIER", result.getId().toString(),
                "Création dossier : " + result.getNumber(),
                httpRequest);

        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }


    @PatchMapping("/{id}/register")
    @PreAuthorize("hasAnyRole('AGENT_BRPD','ADMIN_DDIC')")
    public ResponseEntity<DossierResponse> registerReception(
            @PathVariable UUID id,
            @Valid @RequestBody StatusTransitionRequest request,
            HttpServletRequest httpRequest,
            @AuthenticationPrincipal Jwt jwt) {

        log.info("Enregistrement officiel dossier {} par BRPD", id);
        DossierResponse result = dossierService.registerReception(
                id, request, getClientIp(httpRequest));

        auditService.logAction(
                agentId(jwt), agentName(jwt), agentRole(jwt),
                "ENREGISTRER_DOSSIER", "DOSSIER", id.toString(),
                "Enregistrement réception dossier", httpRequest);

        return ResponseEntity.ok(result);
    }

    @PatchMapping("/{id}/start-study")
    @PreAuthorize("hasAnyRole('CONSEILLER_JURIDIQUE','ADMIN_DDIC')")
    public ResponseEntity<DossierResponse> startOpportunityStudy(
            @PathVariable UUID id,
            @Valid @RequestBody StatusTransitionRequest request,
            HttpServletRequest httpRequest,
            @AuthenticationPrincipal Jwt jwt) {

        log.info("Démarrage étude d'opportunité — dossier {}", id);
        DossierResponse result = dossierService.startOpportunityStudy(
                id, request, getClientIp(httpRequest));

        auditService.logAction(
                agentId(jwt), agentName(jwt), agentRole(jwt),
                "DEMARRER_ETUDE", "DOSSIER", id.toString(),
                "Démarrage étude d'opportunité", httpRequest);

        return ResponseEntity.ok(result);
    }

    @PatchMapping("/{id}/request-complement")
    @PreAuthorize("hasAnyRole('CONSEILLER_JURIDIQUE','MEMBRE_CTADP','ADMIN_DDIC')")
    public ResponseEntity<DossierResponse> requestComplement(
            @PathVariable UUID id,
            @Valid @RequestBody StatusTransitionRequest request,
            HttpServletRequest httpRequest,
            @AuthenticationPrincipal Jwt jwt) {

        log.info("Demande complément — dossier {}", id);
        DossierResponse result = dossierService.requestComplement(
                id, request, getClientIp(httpRequest));

        auditService.logAction(
                agentId(jwt), agentName(jwt), agentRole(jwt),
                "DEMANDER_COMPLEMENT", "DOSSIER", id.toString(),
                "Demande de complément d'information", httpRequest);

        return ResponseEntity.ok(result);
    }

    @PatchMapping("/{id}/complement-received")
    @PreAuthorize("hasAnyRole('AGENT_BRPD','CONSEILLER_JURIDIQUE','ADMIN_DDIC')")
    public ResponseEntity<DossierResponse> complementReceived(
            @PathVariable UUID id,
            @Valid @RequestBody StatusTransitionRequest request,
            HttpServletRequest httpRequest,
            @AuthenticationPrincipal Jwt jwt) {

        log.info("Complément reçu — dossier {}", id);
        DossierResponse result = dossierService.complementReceived(
                id, request, getClientIp(httpRequest));

        auditService.logAction(
                agentId(jwt), agentName(jwt), agentRole(jwt),
                "RECEVOIR_COMPLEMENT", "DOSSIER", id.toString(),
                "Complément d'information reçu", httpRequest);

        return ResponseEntity.ok(result);
    }

    @PatchMapping("/{id}/submit-ctadp")
    @PreAuthorize("hasAnyRole('CONSEILLER_JURIDIQUE','CGEA','ADMIN_DDIC')")
    public ResponseEntity<DossierResponse> submitToCtadp(
            @PathVariable UUID id,
            @Valid @RequestBody StatusTransitionRequest request,
            HttpServletRequest httpRequest,
            @AuthenticationPrincipal Jwt jwt) {

        log.info("Soumission CTADP — dossier {}", id);
        DossierResponse result = dossierService.submitToCtadp(
                id, request, getClientIp(httpRequest));

        auditService.logAction(
                agentId(jwt), agentName(jwt), agentRole(jwt),
                "SOUMETTRE_CTADP", "DOSSIER", id.toString(),
                "Soumission au CTADP", httpRequest);

        return ResponseEntity.ok(result);
    }

    @PatchMapping("/{id}/declare-admissible")
    @PreAuthorize("hasAnyRole('CGE','ADMIN_DDIC')")
    public ResponseEntity<DossierResponse> declareAdmissible(
            @PathVariable UUID id,
            @Valid @RequestBody StatusTransitionRequest request,
            HttpServletRequest httpRequest,
            @AuthenticationPrincipal Jwt jwt) {

        log.info("CGE déclare dossier {} RECEVABLE", id);
        DossierResponse result = dossierService.declareAdmissible(
                id, request, getClientIp(httpRequest));

        auditService.logAction(
                agentId(jwt), agentName(jwt), agentRole(jwt),
                "DECLARER_RECEVABLE", "DOSSIER", id.toString(),
                "Dossier déclaré recevable par CGE", httpRequest);

        return ResponseEntity.ok(result);
    }

    @PatchMapping("/{id}/declare-inadmissible")
    @PreAuthorize("hasAnyRole('CGE','ADMIN_DDIC')")
    public ResponseEntity<DossierResponse> declareInadmissible(
            @PathVariable UUID id,
            @Valid @RequestBody StatusTransitionRequest request,
            HttpServletRequest httpRequest,
            @AuthenticationPrincipal Jwt jwt) {

        log.info("CGE déclare dossier {} IRRECEVABLE", id);
        DossierResponse result = dossierService.declareInadmissible(
                id, request, getClientIp(httpRequest));

        auditService.logAction(
                agentId(jwt), agentName(jwt), agentRole(jwt),
                "DECLARER_IRRECEVABLE", "DOSSIER", id.toString(),
                "Dossier déclaré irrecevable par CGE", httpRequest);

        return ResponseEntity.ok(result);
    }

    @PatchMapping("/{id}/transfer")
    @PreAuthorize("hasAnyRole('CGE','CGEA','ADMIN_DDIC')")
    public ResponseEntity<DossierResponse> transfer(
            @PathVariable UUID id,
            @Valid @RequestBody StatusTransitionRequest request,
            HttpServletRequest httpRequest,
            @AuthenticationPrincipal Jwt jwt) {

        log.info("Transfert dossier {} vers {}", id, request.getTransferInstitution());
        DossierResponse result = dossierService.transfer(
                id, request, getClientIp(httpRequest));

        auditService.logAction(
                agentId(jwt), agentName(jwt), agentRole(jwt),
                "TRANSFERER_DOSSIER", "DOSSIER", id.toString(),
                "Transfert vers : " + request.getTransferInstitution(), httpRequest);

        return ResponseEntity.ok(result);
    }

    @PatchMapping("/{id}/close")
    @PreAuthorize("hasAnyRole('CGE','CGEA','ADMIN_DDIC')")
    public ResponseEntity<DossierResponse> close(
            @PathVariable UUID id,
            @Valid @RequestBody StatusTransitionRequest request,
            HttpServletRequest httpRequest,
            @AuthenticationPrincipal Jwt jwt) {

        log.info("Clôture dossier {}", id);
        DossierResponse result = dossierService.close(
                id, request, getClientIp(httpRequest));

        auditService.logAction(
                agentId(jwt), agentName(jwt), agentRole(jwt),
                "CLOTURER_DOSSIER", "DOSSIER", id.toString(),
                "Clôture du dossier", httpRequest);

        return ResponseEntity.ok(result);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('AGENT_BRPD','CONSEILLER_JURIDIQUE','ADMIN_DDIC')")
    public ResponseEntity<DossierResponse> update(
            @PathVariable UUID id,
            @Valid @RequestBody DossierUpdateRequest request,
            HttpServletRequest httpRequest,
            @AuthenticationPrincipal Jwt jwt) {

        log.info("Mise à jour contenu dossier {}", id);
        DossierResponse result = dossierService.update(id, request);

        auditService.logAction(
                agentId(jwt), agentName(jwt), agentRole(jwt),
                "MODIFIER_DOSSIER", "DOSSIER", id.toString(),
                "Modification du contenu du dossier", httpRequest);

        return ResponseEntity.ok(result);
    }

    @PatchMapping("/{id}/confidential")
    @PreAuthorize("hasAnyRole('CGE','CGEA','ADMIN_DDIC')")
    public ResponseEntity<DossierResponse> toggleConfidential(
            @PathVariable UUID id,
            @RequestParam boolean value,
            @RequestBody StatusTransitionRequest request,
            HttpServletRequest httpRequest,
            @AuthenticationPrincipal Jwt jwt) {

        log.info("Changement confidentialité dossier {} → {}", id, value);
        DossierResponse result = dossierService.setConfidential(id, value, request);

        auditService.logAction(
                agentId(jwt), agentName(jwt), agentRole(jwt),
                "CHANGER_CONFIDENTIALITE", "DOSSIER", id.toString(),
                "Confidentialité → " + (value ? "CONFIDENTIEL" : "PUBLIC"), httpRequest);

        return ResponseEntity.ok(result);
    }


    @PatchMapping("/{id}/priority")
    @PreAuthorize("hasAnyRole('CGE','CGEA','ADMIN_DDIC')")
    public ResponseEntity<DossierResponse> setPriority(
            @PathVariable UUID id,
            @RequestBody @Valid SetPriorityRequest request,
            HttpServletRequest httpRequest,
            @AuthenticationPrincipal Jwt jwt) {

        log.info("[Priority §A.2.1] Dossier {} → {} par {}",
                id, request.getPriority(),
                SecurityContextHolder.getContext()
                        .getAuthentication().getName());

        DossierResponse result = dossierService.setPriority(
                id, request, getClientIp(httpRequest));

        auditService.logAction(
                agentId(jwt), agentName(jwt), agentRole(jwt),
                "DEFINIR_PRIORITE", "DOSSIER", id.toString(),
                "Priorité → " + request.getPriority(), httpRequest);

        return ResponseEntity.ok(result);
    }
}