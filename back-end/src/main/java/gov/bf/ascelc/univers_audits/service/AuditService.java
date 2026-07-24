package gov.bf.ascelc.univers_audits.service;

import gov.bf.ascelc.univers_audits.mapper.AuditMapper;
import gov.bf.ascelc.univers_audits.model.dto.response.AuditLogResponse;
import gov.bf.ascelc.univers_audits.model.dto.response.LoginLogResponse;
import gov.bf.ascelc.univers_audits.model.entity.AuditLog;
import gov.bf.ascelc.univers_audits.model.entity.LoginLog;
import gov.bf.ascelc.univers_audits.repository.AuditLogRepository;
import gov.bf.ascelc.univers_audits.repository.LoginLogRepository;
import jakarta.persistence.criteria.Predicate;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository auditRepo;
    private final LoginLogRepository loginRepo;
    private final AuditMapper        auditMapper;


    /**
     * IP/user-agent doivent être extraits du HttpServletRequest par l'appelant
     * (synchrone, dans le thread de la requête HTTP) — cette méthode est
     * @Async et peut s'exécuter après la fin du cycle de requête d'origine,
     * moment où request.getHeader(...)/getRemoteAddr() ne sont plus fiables.
     */
    @Async
    public void logAction(String agentId,
                          String agentName,
                          String agentRole,
                          String action,
                          String entityType,
                          String entityId,
                          String description,
                          String ipAddress,
                          String userAgent) {
        try {
            auditRepo.save(AuditLog.builder()
                    .agentId(agentId != null ? agentId : "SYSTEM")
                    .agentName(agentName)
                    .agentRole(agentRole)
                    .action(action)
                    .entityType(entityType)
                    .entityId(entityId)
                    .description(description)
                    .ipAddress(ipAddress)
                    .userAgent(userAgent)
                    .status("SUCCESS")
                    .build());
        } catch (Exception e) {
            log.error("[Audit] Erreur enregistrement action '{}' : {}", action, e.getMessage());
        }
    }

    @Async
    public void logLogin(String agentId,
                         String agentName,
                         boolean success,
                         String failureReason,
                         String ipAddress,
                         String userAgent) {
        try {
            loginRepo.save(LoginLog.builder()
                    .agentId(agentId != null ? agentId : "UNKNOWN")
                    .agentName(agentName)
                    .success(success)
                    .failureReason(failureReason)
                    .ipAddress(ipAddress)
                    .userAgent(userAgent)
                    .build());
        } catch (Exception e) {
            log.error("[Audit] Erreur login log agent '{}' : {}", agentId, e.getMessage());
        }
    }

    /** À appeler de façon synchrone, dans le thread de la requête HTTP. */
    public static String extractIp(HttpServletRequest request) {
        if (request == null) return null;
        String xff = request.getHeader("X-Forwarded-For");
        return (xff != null && !xff.isBlank())
                ? xff.split(",")[0].trim()
                : request.getRemoteAddr();
    }

    /** À appeler de façon synchrone, dans le thread de la requête HTTP. */
    public static String extractUserAgent(HttpServletRequest request) {
        return request != null ? request.getHeader("User-Agent") : null;
    }

    public Page<AuditLogResponse> getLogs(String agentName, String action,
                                          LocalDate dateFrom, LocalDate dateTo,
                                          Pageable pageable) {
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

        return auditRepo.findAll(spec, pageable).map(auditMapper::toResponse);
    }

    public Page<LoginLogResponse> getLogins(String agentName, Pageable pageable) {
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

        return loginRepo.findAll(spec, pageable).map(auditMapper::toResponse);
    }

    public Map<String, Object> getStats() {
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

        return stats;
    }
}