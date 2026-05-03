package gov.bf.ascelc.univers_audits.controller;

import gov.bf.ascelc.univers_audits.model.dto.request.CreateAgentRequest;
import gov.bf.ascelc.univers_audits.model.dto.request.UpdateAgentRequest;
import gov.bf.ascelc.univers_audits.model.entity.Agent;
import gov.bf.ascelc.univers_audits.service.AgentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/agents")
@RequiredArgsConstructor
public class AgentController {

    private final AgentService agentService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN_DDIC','CGE','CGEA')")
    public ResponseEntity<?> findAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Page<Agent> result = agentService.findAll(page, size);

        return ResponseEntity.ok(Map.of(
                "content",       result.getContent().stream()
                        .map(this::toMap).toList(),
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
    public ResponseEntity<?> findActive() {
        Page<Agent> page = agentService.findAll(0, 500);
        return ResponseEntity.ok(
                page.getContent().stream()
                        .filter(a -> Boolean.TRUE.equals(a.getActif()))
                        .map(a -> Map.of(
                                "id",        a.getId().toString(),
                                "matricule", a.getMatricule(),
                                "firstName", a.getFirstName(),
                                "lastName",  a.getLastName(),
                                "email",     a.getEmail()
                        ))
                        .toList()
        );
    }

    @GetMapping("/keycloak-roles")
    @PreAuthorize("hasAnyRole('ADMIN_DDIC','CGE')")
    public ResponseEntity<List<String>> getAvailableRoles() {
        return ResponseEntity.ok(agentService.getAvailableKeycloakRoles());
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN_DDIC','CGE')")
    public ResponseEntity<?> create(@Valid @RequestBody CreateAgentRequest req) {
        Agent agent = agentService.createAgent(req);
        return ResponseEntity.status(201).body(toMap(agent));
    }


    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN_DDIC','CGE','CGEA')")
    public ResponseEntity<?> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(toMap(agentService.findById(id)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN_DDIC','CGE')")
    public ResponseEntity<?> update(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateAgentRequest req) {
        return ResponseEntity.ok(toMap(agentService.updateAgent(id, req)));
    }

    @GetMapping("/{id}/keycloak-roles")
    @PreAuthorize("hasAnyRole('ADMIN_DDIC','CGE','CGEA')")
    public ResponseEntity<List<String>> getAgentRoles(@PathVariable UUID id) {
        return ResponseEntity.ok(agentService.getAgentKeycloakRoles(id));
    }

    @PutMapping("/{id}/keycloak-roles")
    @PreAuthorize("hasAnyRole('ADMIN_DDIC','CGE')")
    public ResponseEntity<Void> updateAgentRoles(
            @PathVariable UUID id,
            @RequestBody List<String> roles) {
        agentService.updateAgentRoles(id, roles);
        return ResponseEntity.ok().build();
    }

    @PatchMapping("/{id}/activate")
    @PreAuthorize("hasAnyRole('ADMIN_DDIC','CGE')")
    public ResponseEntity<?> activate(@PathVariable UUID id) {
        Agent a = agentService.activate(id);
        return ResponseEntity.ok(Map.of(
                "id",   a.getId().toString(),
                "actif", true
        ));
    }

    @PatchMapping("/{id}/deactivate")
    @PreAuthorize("hasAnyRole('ADMIN_DDIC','CGE')")
    public ResponseEntity<?> deactivate(@PathVariable UUID id) {
        Agent a = agentService.deactivate(id);
        return ResponseEntity.ok(Map.of(
                "id",   a.getId().toString(),
                "actif", false
        ));
    }

    // ── Mapping privé ─────────────────────────────────────────

    private Map<String, Object> toMap(Agent a) {
        return Map.of(
                "id",         a.getId().toString(),
                "matricule",  a.getMatricule(),
                "firstName",  a.getFirstName(),
                "lastName",   a.getLastName(),
                "email",      a.getEmail(),
                "actif",      Boolean.TRUE.equals(a.getActif()),
                "grade",      a.getGrade()      != null ? a.getGrade()      : "",
                "keycloakId", a.getKeycloakId() != null ? a.getKeycloakId() : "",
                "createdAt",  a.getCreatedAt()  != null ? a.getCreatedAt().toString() : ""
        );
    }
}