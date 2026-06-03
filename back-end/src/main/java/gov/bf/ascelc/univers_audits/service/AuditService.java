package gov.bf.ascelc.univers_audits.service;

import gov.bf.ascelc.univers_audits.model.entity.AuditLog;
import gov.bf.ascelc.univers_audits.model.entity.LoginLog;
import gov.bf.ascelc.univers_audits.repository.AuditLogRepository;
import gov.bf.ascelc.univers_audits.repository.LoginLogRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository auditRepo;
    private final LoginLogRepository loginRepo;


    @Async
    public void logAction(String agentId,
                          String agentName,
                          String agentRole,
                          String action,
                          String entityType,
                          String entityId,
                          String description,
                          HttpServletRequest request) {
        try {
            auditRepo.save(AuditLog.builder()
                    .agentId(agentId != null ? agentId : "SYSTEM")
                    .agentName(agentName)
                    .agentRole(agentRole)
                    .action(action)
                    .entityType(entityType)
                    .entityId(entityId)
                    .description(description)
                    .ipAddress(extractIp(request))
                    .userAgent(request != null ? request.getHeader("User-Agent") : null)
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
                         HttpServletRequest request) {
        try {
            loginRepo.save(LoginLog.builder()
                    .agentId(agentId != null ? agentId : "UNKNOWN")
                    .agentName(agentName)
                    .success(success)
                    .failureReason(failureReason)
                    .ipAddress(extractIp(request))
                    .userAgent(request != null ? request.getHeader("User-Agent") : null)
                    .build());
        } catch (Exception e) {
            log.error("[Audit] Erreur login log agent '{}' : {}", agentId, e.getMessage());
        }
    }

    private String extractIp(HttpServletRequest request) {
        if (request == null) return null;
        String xff = request.getHeader("X-Forwarded-For");
        return (xff != null && !xff.isBlank())
                ? xff.split(",")[0].trim()
                : request.getRemoteAddr();
    }
}