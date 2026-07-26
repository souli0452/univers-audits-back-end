package gov.bf.ascelc.univers_audits.service;

import gov.bf.ascelc.univers_audits.model.dto.request.PvAuditionCreateRequest;
import gov.bf.ascelc.univers_audits.model.dto.request.PvAuditionFinalizeRequest;
import gov.bf.ascelc.univers_audits.model.dto.response.PvAuditionResponse;

import java.util.UUID;

public interface PvAuditionService {

    PvAuditionResponse create(UUID auditionId, PvAuditionCreateRequest request);

    PvAuditionResponse finalizeSignatures(UUID auditionId, PvAuditionFinalizeRequest request);

    PvAuditionResponse findByAuditionId(UUID auditionId);
}
