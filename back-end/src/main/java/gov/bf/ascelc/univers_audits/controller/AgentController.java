package gov.bf.ascelc.univers_audits.controller;

import gov.bf.ascelc.univers_audits.mapper.AgentMapper;
import gov.bf.ascelc.univers_audits.model.dto.request.CreateAgentRequest;
import gov.bf.ascelc.univers_audits.model.dto.request.UpdateAgentRequest;
import gov.bf.ascelc.univers_audits.model.dto.response.AgentResponse;
import gov.bf.ascelc.univers_audits.model.dto.response.AgentSummaryResponse;
import gov.bf.ascelc.univers_audits.model.entity.Agent;
import gov.bf.ascelc.univers_audits.service.AgentService;
import gov.bf.ascelc.univers_audits.service.AuditService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/agents")
@RequiredArgsConstructor
public class AgentController {

    private final AgentService  agentService;
    private final AuditService  auditService;
    private final AgentMapper   agentMapper;


    private String getClientIp(HttpServletRequest req) {
        String xff = req.getHeader("X-Forwarded-For");
        return (xff != null && !xff.isBlank()) ? xff.split(",")[0].trim() : req.getRemoteAddr();
    }

    private String id(Jwt jwt)   { return jwt != null ? jwt.getSubject()                  : "SYSTEM"; }
    private String name(Jwt jwt) { return jwt != null ? jwt.getClaimAsString("name")      : null; }
    private String role(Jwt jwt) {
        if (jwt == null) return null;
        var r = jwt.getClaimAsStringList("roles");
        return (r != null && !r.isEmpty()) ? r.get(0) : null;
    }


    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN_DDIC','CGE','CGEA')")
    public ResponseEntity<?> findAll(
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<Agent> result = agentService.findAll(page, size);
        return ResponseEntity.ok(Map.of(
                "content",       result.getContent().stream().map(agentMapper::toResponse).toList(),
                "totalElements", result.getTotalElements(),
                "totalPages",    result.getTotalPages(),
                "size",          result.getSize(),
                "number",        result.getNumber(),
                "first",         result.isFirst(),
                "last",          result.isLast()
        ));
    }

    @GetMapping("/active")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<AgentSummaryResponse>> findActive() {
        Page<Agent> page = agentService.findAll(0, 500);
        return ResponseEntity.ok(
                page.getContent().stream()
                        .filter(a -> Boolean.TRUE.equals(a.getActif()))
                        .map(agentMapper::toSummaryResponse)
                        .toList()
        );
    }

    @GetMapping("/keycloak-roles")
    @PreAuthorize("hasAnyRole('ADMIN_DDIC','CGE')")
    public ResponseEntity<List<String>> getAvailableRoles() {
        return ResponseEntity.ok(agentService.getAvailableKeycloakRoles());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN_DDIC','CGE','CGEA')")
    public ResponseEntity<AgentResponse> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(agentMapper.toResponse(agentService.findById(id)));
    }

    @GetMapping("/{id}/keycloak-roles")
    @PreAuthorize("hasAnyRole('ADMIN_DDIC','CGE','CGEA')")
    public ResponseEntity<List<String>> getAgentRoles(@PathVariable UUID id) {
        return ResponseEntity.ok(agentService.getAgentKeycloakRoles(id));
    }


    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN_DDIC','CGE')")
    public ResponseEntity<AgentResponse> create(
            @Valid @RequestBody CreateAgentRequest req,
            HttpServletRequest httpRequest,
            @AuthenticationPrincipal Jwt jwt) {

        Agent agent = agentService.createAgent(req);

        auditService.logAction(
                id(jwt), name(jwt), role(jwt),
                "CREER_AGENT", "AGENT", agent.getId().toString(),
                "Création agent : " + agent.getFirstName() + " " + agent.getLastName()
                        + " (" + agent.getMatricule() + ")",
                AuditService.extractIp(httpRequest),
                AuditService.extractUserAgent(httpRequest));

        return ResponseEntity.status(201).body(agentMapper.toResponse(agent));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN_DDIC','CGE')")
    public ResponseEntity<AgentResponse> update(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateAgentRequest req,
            HttpServletRequest httpRequest,
            @AuthenticationPrincipal Jwt jwt) {

        Agent agent = agentService.updateAgent(id, req);

        auditService.logAction(
                id(jwt), name(jwt), role(jwt),
                "MODIFIER_AGENT", "AGENT", id.toString(),
                "Modification agent : " + agent.getFirstName() + " " + agent.getLastName(),
                AuditService.extractIp(httpRequest),
                AuditService.extractUserAgent(httpRequest));

        return ResponseEntity.ok(agentMapper.toResponse(agent));
    }

    @PutMapping("/{id}/keycloak-roles")
    @PreAuthorize("hasAnyRole('ADMIN_DDIC','CGE')")
    public ResponseEntity<Void> updateAgentRoles(
            @PathVariable UUID id,
            @RequestBody List<String> roles,
            HttpServletRequest httpRequest,
            @AuthenticationPrincipal Jwt jwt) {

        agentService.updateAgentRoles(id, roles);

        auditService.logAction(
                id(jwt), name(jwt), role(jwt),
                "MODIFIER_ROLES_AGENT", "AGENT", id.toString(),
                "Modification rôles Keycloak → " + String.join(", ", roles),
                AuditService.extractIp(httpRequest),
                AuditService.extractUserAgent(httpRequest));

        return ResponseEntity.ok().build();
    }

    @PatchMapping("/{id}/activate")
    @PreAuthorize("hasAnyRole('ADMIN_DDIC','CGE')")
    public ResponseEntity<?> activate(
            @PathVariable UUID id,
            HttpServletRequest httpRequest,
            @AuthenticationPrincipal Jwt jwt) {

        Agent a = agentService.activate(id);

        auditService.logAction(
                id(jwt), name(jwt), role(jwt),
                "ACTIVER_AGENT", "AGENT", id.toString(),
                "Activation agent : " + a.getFirstName() + " " + a.getLastName(),
                AuditService.extractIp(httpRequest),
                AuditService.extractUserAgent(httpRequest));

        return ResponseEntity.ok(Map.of("id", a.getId().toString(), "actif", true));
    }

    @PatchMapping("/{id}/deactivate")
    @PreAuthorize("hasAnyRole('ADMIN_DDIC','CGE')")
    public ResponseEntity<?> deactivate(
            @PathVariable UUID id,
            HttpServletRequest httpRequest,
            @AuthenticationPrincipal Jwt jwt) {

        Agent a = agentService.deactivate(id);

        auditService.logAction(
                id(jwt), name(jwt), role(jwt),
                "DESACTIVER_AGENT", "AGENT", id.toString(),
                "Désactivation agent : " + a.getFirstName() + " " + a.getLastName(),
                AuditService.extractIp(httpRequest),
                AuditService.extractUserAgent(httpRequest));

        return ResponseEntity.ok(Map.of("id", a.getId().toString(), "actif", false));
    }
}
