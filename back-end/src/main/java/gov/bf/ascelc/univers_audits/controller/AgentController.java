package gov.bf.ascelc.univers_audits.controller;

import gov.bf.ascelc.univers_audits.model.entity.Agent;
import gov.bf.ascelc.univers_audits.repository.AgentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/agents")
@RequiredArgsConstructor
public class AgentController {

    private final AgentRepository agentRepository;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN_DDIC','CGE','CGEA')")
    public ResponseEntity<?> findAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String search) {

        PageRequest pageable = PageRequest.of(
                page, size, Sort.by("lastName").ascending()
        );

        Page<Agent> result = agentRepository.findAll(pageable);

        return ResponseEntity.ok(Map.of(
                "content", result.getContent().stream().map(a -> Map.of(
                        "id", a.getId().toString(),
                        "matricule", a.getMatricule(),
                        "firstName", a.getFirstName(),
                        "lastName", a.getLastName(),
                        "email", a.getEmail(),
                        "actif", Boolean.TRUE.equals(a.getActif()),
                        "grade", a.getGrade() != null ? a.getGrade() : "",
                        "keycloakId", a.getKeycloakId() != null
                                ? a.getKeycloakId() : "",
                        "createdAt", a.getCreatedAt() != null
                                ? a.getCreatedAt().toString() : ""
                )).toList(),
                "totalElements", result.getTotalElements(),
                "totalPages", result.getTotalPages(),
                "size", result.getSize(),
                "number", result.getNumber(),
                "first", result.isFirst(),
                "last", result.isLast()
        ));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN_DDIC','CGE','CGEA')")
    public ResponseEntity<?> findById(@PathVariable UUID id) {
        return agentRepository.findById(id)
                .map(a -> ResponseEntity.ok(Map.of(
                        "id", a.getId().toString(),
                        "matricule", a.getMatricule(),
                        "firstName", a.getFirstName(),
                        "lastName", a.getLastName(),
                        "email", a.getEmail(),
                        "actif", Boolean.TRUE.equals(a.getActif()),
                        "grade", a.getGrade() != null ? a.getGrade() : "",
                        "createdAt", a.getCreatedAt() != null
                                ? a.getCreatedAt().toString() : ""
                )))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/active")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> findActive() {
        return ResponseEntity.ok(
                agentRepository.findAll().stream()
                        .filter(a -> Boolean.TRUE.equals(a.getActif()))
                        .map(a -> Map.of(
                                "id", a.getId().toString(),
                                "matricule", a.getMatricule(),
                                "firstName", a.getFirstName(),
                                "lastName", a.getLastName(),
                                "email", a.getEmail()
                        ))
                        .toList()
        );
    }

    @PatchMapping("/{id}/activate")
    @PreAuthorize("hasAnyRole('ADMIN_DDIC','CGE')")
    public ResponseEntity<?> activate(@PathVariable UUID id) {
        return agentRepository.findById(id).map(a -> {
            a.setActif(true);
            agentRepository.save(a);
            return ResponseEntity.ok(Map.of(
                    "id", a.getId().toString(),
                    "actif", true,
                    "message", "Agent active"
            ));
        }).orElse(ResponseEntity.notFound().build());
    }

    @PatchMapping("/{id}/deactivate")
    @PreAuthorize("hasAnyRole('ADMIN_DDIC','CGE')")
    public ResponseEntity<?> deactivate(@PathVariable UUID id) {
        return agentRepository.findById(id).map(a -> {
            a.setActif(false);
            agentRepository.save(a);
            return ResponseEntity.ok(Map.of(
                    "id", a.getId().toString(),
                    "actif", false,
                    "message", "Agent desactive"
            ));
        }).orElse(ResponseEntity.notFound().build());
    }
}