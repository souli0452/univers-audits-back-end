package gov.bf.ascelc.univers_audits.mapper;

import gov.bf.ascelc.univers_audits.model.dto.response.AuditLogResponse;
import gov.bf.ascelc.univers_audits.model.dto.response.LoginLogResponse;
import gov.bf.ascelc.univers_audits.model.entity.AuditLog;
import gov.bf.ascelc.univers_audits.model.entity.LoginLog;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(
        componentModel = "spring",
        unmappedTargetPolicy = ReportingPolicy.IGNORE
)
public interface AuditMapper {

    AuditLogResponse toResponse(AuditLog auditLog);

    LoginLogResponse toResponse(LoginLog loginLog);
}
