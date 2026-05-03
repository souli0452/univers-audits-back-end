package gov.bf.ascelc.univers_audits.service;

import gov.bf.ascelc.univers_audits.model.dto.request.WitnessRequest;
import gov.bf.ascelc.univers_audits.model.dto.response.WitnessResponse;

import java.util.List;
import java.util.UUID;

public interface WitnessService {

    List<WitnessResponse> findByDossierId(UUID dossierId);

    WitnessResponse create(UUID dossierId, WitnessRequest request);

    WitnessResponse update(UUID dossierId,
                           UUID witnessId,
                           WitnessRequest request);

    void delete(UUID dossierId, UUID witnessId);
}