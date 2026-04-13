package gov.bf.ascelc.univers_audits.controller;

import gov.bf.ascelc.univers_audits.enums.DossierStatus;
import gov.bf.ascelc.univers_audits.model.dto.request.DossierCreateRequest;
import gov.bf.ascelc.univers_audits.model.dto.request.DossierUpdateRequest;
import gov.bf.ascelc.univers_audits.model.dto.request.StatusTransitionRequest;
import gov.bf.ascelc.univers_audits.model.dto.response.DossierResponse;
import gov.bf.ascelc.univers_audits.service.DossierService;
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
 * ═══════════════════════════════════════════════════════════════
 *  DossierController — API REST des dossiers ASCE-LC
 * ═══════════════════════════════════════════════════════════════
 *
 *  BASE URL : /api/v1/dossiers
 *
 *  WORKFLOW MANUEL B :
 *  POST   /public/submit          → Citoyen soumet (sans auth)
 *  GET    /public/track/{code}    → Citoyen suit (sans auth)
 *  PATCH  /{id}/register          → BRPD enregistre (SOUMIS→RECU)
 *  PATCH  /{id}/start-study       → Conseiller démarre étude
 *  PATCH  /{id}/request-complement→ Complément demandé
 *  PATCH  /{id}/complement-received→ Complément reçu
 *  PATCH  /{id}/submit-ctadp      → Soumis au CTADP
 *  PATCH  /{id}/declare-admissible → CGE : RECEVABLE
 *  PATCH  /{id}/declare-inadmissible→ CGE : IRRECEVABLE
 *  PATCH  /{id}/transfer          → CGE : TRANSFERE
 *  PATCH  /{id}/close             → Clôture finale
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping(ApiUrls.DOSSIERS)
public class DossierController {

    private final DossierService dossierService;

    @GetMapping("/public/track/{accessCode}")
    public ResponseEntity<DossierResponse> trackByAccessCode(
            @PathVariable String accessCode) {
        log.info("Suivi public — accessCode: {}", accessCode);
        return ResponseEntity.ok(
                dossierService.findByAccessCode(accessCode));
    }

    @PostMapping("/public/submit")
    public ResponseEntity<DossierResponse> submitPublic(
            @Valid @RequestBody DossierCreateRequest request,
            HttpServletRequest httpRequest) {
        log.info("Soumission publique — mode: {}",
                request.getSubmissionMode());
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(dossierService.submit(
                        request, getClientIp(httpRequest)));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('AGENT_BRPD', 'CONSEILLER_JURIDIQUE',"
            + "'MEMBRE_CTADP', 'CGEA', 'CGE', 'ADMIN_DDIC')")
    public ResponseEntity<Page<DossierResponse>> findAll(
            @PageableDefault(size = 20, sort = "createdAt")
            Pageable pageable) {
        return ResponseEntity.ok(
                dossierService.findAll(pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('AGENT_BRPD', 'CONSEILLER_JURIDIQUE',"
            + "'MEMBRE_CTADP', 'CGEA', 'CGE', 'ADMIN_DDIC')")
    public ResponseEntity<DossierResponse> findById(
            @PathVariable UUID id) {
        return ResponseEntity.ok(dossierService.findById(id));
    }

    @GetMapping("/status/{status}")
    @PreAuthorize("hasAnyRole('AGENT_BRPD', 'CONSEILLER_JURIDIQUE',"
            + "'MEMBRE_CTADP', 'CGEA', 'CGE', 'ADMIN_DDIC')")
    public ResponseEntity<Page<DossierResponse>> findByStatus(
            @PathVariable DossierStatus status,
            @PageableDefault(size = 20, sort = "createdAt")
            Pageable pageable) {
        return ResponseEntity.ok(
                dossierService.findByStatus(status, pageable));
    }

    @GetMapping("/my")
    @PreAuthorize("hasAnyRole('AGENT_BRPD', 'CONSEILLER_JURIDIQUE',"
            + "'CONTROLEUR_ETAT', 'CGEA')")
    public ResponseEntity<Page<DossierResponse>> findMyDossiers(
            @PageableDefault(size = 20, sort = "createdAt")
            Pageable pageable) {
        return ResponseEntity.ok(
                dossierService.findMyDossiers(pageable));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('AGENT_BRPD', 'ADMIN_DDIC')")
    public ResponseEntity<DossierResponse> create(
            @Valid @RequestBody DossierCreateRequest request,
            HttpServletRequest httpRequest) {
        log.info("Création dossier par agent BRPD — mode: {}",
                request.getSubmissionMode());
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(dossierService.submit(
                        request, getClientIp(httpRequest)));
    }

    @PatchMapping("/{id}/register")
    @PreAuthorize("hasAnyRole('AGENT_BRPD', 'ADMIN_DDIC')")
    public ResponseEntity<DossierResponse> registerReception(
            @PathVariable UUID id,
            @Valid @RequestBody StatusTransitionRequest request,
            HttpServletRequest httpRequest) {
        log.info("Enregistrement officiel dossier {} par BRPD", id);
        return ResponseEntity.ok(
                dossierService.registerReception(
                        id, request, getClientIp(httpRequest)));
    }

    @PatchMapping("/{id}/start-study")
    @PreAuthorize("hasAnyRole('CONSEILLER_JURIDIQUE', 'ADMIN_DDIC')")
    public ResponseEntity<DossierResponse> startOpportunityStudy(
            @PathVariable UUID id,
            @Valid @RequestBody StatusTransitionRequest request,
            HttpServletRequest httpRequest) {
        log.info("Démarrage étude d'opportunité — dossier {}", id);
        return ResponseEntity.ok(
                dossierService.startOpportunityStudy(
                        id, request, getClientIp(httpRequest)));
    }

    @PatchMapping("/{id}/request-complement")
    @PreAuthorize("hasAnyRole('CONSEILLER_JURIDIQUE',"
            + "'MEMBRE_CTADP', 'ADMIN_DDIC')")
    public ResponseEntity<DossierResponse> requestComplement(
            @PathVariable UUID id,
            @Valid @RequestBody StatusTransitionRequest request,
            HttpServletRequest httpRequest) {
        log.info("Demande complément — dossier {}", id);
        return ResponseEntity.ok(
                dossierService.requestComplement(
                        id, request, getClientIp(httpRequest)));
    }

    @PatchMapping("/{id}/complement-received")
    @PreAuthorize("hasAnyRole('AGENT_BRPD', 'CONSEILLER_JURIDIQUE',"
            + "'ADMIN_DDIC')")
    public ResponseEntity<DossierResponse> complementReceived(
            @PathVariable UUID id,
            @Valid @RequestBody StatusTransitionRequest request,
            HttpServletRequest httpRequest) {
        log.info("Complément reçu — dossier {}", id);
        return ResponseEntity.ok(
                dossierService.complementReceived(
                        id, request, getClientIp(httpRequest)));
    }

    @PatchMapping("/{id}/submit-ctadp")
    @PreAuthorize("hasAnyRole('CONSEILLER_JURIDIQUE', 'CGEA',"
            + "'ADMIN_DDIC')")
    public ResponseEntity<DossierResponse> submitToCtadp(
            @PathVariable UUID id,
            @Valid @RequestBody StatusTransitionRequest request,
            HttpServletRequest httpRequest) {
        log.info("Soumission CTADP — dossier {}", id);
        return ResponseEntity.ok(
                dossierService.submitToCtadp(
                        id, request, getClientIp(httpRequest)));
    }

    @PatchMapping("/{id}/declare-admissible")
    @PreAuthorize("hasAnyRole('CGE', 'ADMIN_DDIC')")
    public ResponseEntity<DossierResponse> declareAdmissible(
            @PathVariable UUID id,
            @Valid @RequestBody StatusTransitionRequest request,
            HttpServletRequest httpRequest) {
        log.info("CGE déclare dossier {} RECEVABLE", id);
        return ResponseEntity.ok(
                dossierService.declareAdmissible(
                        id, request, getClientIp(httpRequest)));
    }


    @PatchMapping("/{id}/declare-inadmissible")
    @PreAuthorize("hasAnyRole('CGE', 'ADMIN_DDIC')")
    public ResponseEntity<DossierResponse> declareInadmissible(
            @PathVariable UUID id,
            @Valid @RequestBody StatusTransitionRequest request,
            HttpServletRequest httpRequest) {
        log.info("CGE déclare dossier {} IRRECEVABLE", id);
        return ResponseEntity.ok(
                dossierService.declareInadmissible(
                        id, request, getClientIp(httpRequest)));
    }


    @PatchMapping("/{id}/transfer")
    @PreAuthorize("hasAnyRole('CGE', 'CGEA', 'ADMIN_DDIC')")
    public ResponseEntity<DossierResponse> transfer(
            @PathVariable UUID id,
            @Valid @RequestBody StatusTransitionRequest request,
            HttpServletRequest httpRequest) {
        log.info("Transfert dossier {} vers {}",
                id, request.getTransferInstitution());
        return ResponseEntity.ok(
                dossierService.transfer(
                        id, request, getClientIp(httpRequest)));
    }

    @PatchMapping("/{id}/close")
    @PreAuthorize("hasAnyRole('CGE', 'CGEA', 'ADMIN_DDIC')")
    public ResponseEntity<DossierResponse> close(
            @PathVariable UUID id,
            @Valid @RequestBody StatusTransitionRequest request,
            HttpServletRequest httpRequest) {
        log.info("Clôture dossier {}", id);
        return ResponseEntity.ok(
                dossierService.close(
                        id, request, getClientIp(httpRequest)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('AGENT_BRPD', 'CONSEILLER_JURIDIQUE',"
            + "'ADMIN_DDIC')")
    public ResponseEntity<DossierResponse> update(
            @PathVariable UUID id,
            @Valid @RequestBody DossierUpdateRequest request) {
        log.info("Mise à jour contenu dossier {}", id);
        return ResponseEntity.ok(
                dossierService.update(id, request));
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