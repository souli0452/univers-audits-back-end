package gov.bf.ascelc.univers_audits.service;

import gov.bf.ascelc.univers_audits.model.dto.request.PvConstatCreateRequest;
import gov.bf.ascelc.univers_audits.model.dto.response.PvConstatResponse;

import java.util.UUID;

public interface PvConstatService {

    PvConstatResponse create(UUID visiteId, PvConstatCreateRequest request);

    PvConstatResponse findByVisiteId(UUID visiteId);
}
