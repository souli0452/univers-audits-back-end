package gov.bf.ascelc.univers_audits.controller;

import gov.bf.ascelc.univers_audits.model.entity.AuditLog;
import gov.bf.ascelc.univers_audits.model.entity.LoginLog;
import gov.bf.ascelc.univers_audits.repository.AuditLogRepository;
import gov.bf.ascelc.univers_audits.repository.LoginLogRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/admin/audit")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN_DDIC','CGE')")
public class AuditController {

    private final AuditLogRepository auditRepo;
    private final LoginLogRepository loginRepo;


    @GetMapping
    public ResponseEntity<Page<AuditLog>> getLogs(
            @RequestParam(required = false) String agentName,
            @RequestParam(required = false) String action,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {

        Specification<AuditLog> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (agentName != null && !agentName.isBlank()) {
                predicates.add(cb.like(
                        cb.lower(root.get("agentName")),
                        "%" + agentName.toLowerCase() + "%"
                ));
            }

            if (action != null && !action.isBlank()) {
                predicates.add(cb.equal(root.get("action"), action));
            }

            if (dateFrom != null) {
                Instant from = dateFrom.atStartOfDay(ZoneOffset.UTC).toInstant();
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), from));
            }

            if (dateTo != null) {
                Instant to = dateTo.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
                predicates.add(cb.lessThan(root.get("createdAt"), to));
            }

            query.orderBy(cb.desc(root.get("createdAt")));
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        return ResponseEntity.ok(auditRepo.findAll(spec, PageRequest.of(page, size)));
    }

    @GetMapping("/logins")
    public ResponseEntity<Page<LoginLog>> getLogins(
            @RequestParam(required = false) String agentName,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {

        Specification<LoginLog> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (agentName != null && !agentName.isBlank()) {
                predicates.add(cb.like(
                        cb.lower(root.get("agentName")),
                        "%" + agentName.toLowerCase() + "%"
                ));
            }

            query.orderBy(cb.desc(root.get("createdAt")));
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        return ResponseEntity.ok(loginRepo.findAll(spec, PageRequest.of(page, size)));
    }

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats() {
        Instant since24h = Instant.now().minusSeconds(86_400L);
        Instant since7d  = Instant.now().minusSeconds(86_400L * 7);
        Instant since30d = Instant.now().minusSeconds(86_400L * 30);

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("actionsToday",     auditRepo.countSince(since24h));
        stats.put("actionsWeek",      auditRepo.countSince(since7d));
        stats.put("loginsSuccess30d", loginRepo.countSuccessSince(since30d));
        stats.put("loginsFailed30d",  loginRepo.countFailureSince(since30d));
        stats.put("totalActions",     auditRepo.count());
        stats.put("totalLogins",      loginRepo.count());

        stats.put("topActions", auditRepo.countByAction().stream()
                .limit(5)
                .collect(Collectors.toMap(
                        r -> (String) r[0],
                        r -> (Long)   r[1],
                        (a, b) -> a,
                        LinkedHashMap::new)));

        stats.put("topAgents", auditRepo.countByAgent().stream()
                .limit(5)
                .collect(Collectors.toMap(
                        r -> (String) r[0],
                        r -> (Long)   r[1],
                        (a, b) -> a,
                        LinkedHashMap::new)));

        return ResponseEntity.ok(stats);
    }
}