package gov.bf.ascelc.univers_audits.service;

import gov.bf.ascelc.univers_audits.model.dto.request.AuditionConductRequest;
import gov.bf.ascelc.univers_audits.model.dto.request.AuditionScheduleRequest;
import gov.bf.ascelc.univers_audits.model.dto.response.AuditionResponse;

import java.util.List;
import java.util.UUID;

public interface AuditionService {

    AuditionResponse schedule(UUID investigationId, AuditionScheduleRequest request);

    AuditionResponse conduct(UUID auditionId, AuditionConductRequest request);

    AuditionResponse cancel(UUID auditionId, String reason);

    List<AuditionResponse> findByInvestigationId(UUID investigationId);
}
