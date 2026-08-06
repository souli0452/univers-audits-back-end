package gov.bf.ascelc.univers_audits.service;

import gov.bf.ascelc.univers_audits.model.dto.request.VisiteTerrainConductRequest;
import gov.bf.ascelc.univers_audits.model.dto.request.VisiteTerrainScheduleRequest;
import gov.bf.ascelc.univers_audits.model.dto.response.VisiteTerrainResponse;

import java.util.List;
import java.util.UUID;

public interface VisiteTerrainService {

    VisiteTerrainResponse schedule(UUID investigationId, VisiteTerrainScheduleRequest request);

    VisiteTerrainResponse conduct(UUID visiteId, VisiteTerrainConductRequest request);

    VisiteTerrainResponse cancel(UUID visiteId, String reason);

    VisiteTerrainResponse markCarence(UUID visiteId, String reason);

    List<VisiteTerrainResponse> findByInvestigationId(UUID investigationId);
}
