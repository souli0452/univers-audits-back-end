package gov.bf.ascelc.univers_audits.service;

import gov.bf.ascelc.univers_audits.model.dto.request.TargetedPartyRequest;
import gov.bf.ascelc.univers_audits.model.dto.response.TargetedPartyResponse;

import java.util.List;
import java.util.UUID;

public interface TargetedPartyService {

    List<TargetedPartyResponse> findByDossierId(UUID dossierId);

    TargetedPartyResponse create(UUID dossierId,
                                 TargetedPartyRequest request);

    TargetedPartyResponse update(UUID dossierId,
                                 UUID partyId,
                                 TargetedPartyRequest request);

    void delete(UUID dossierId, UUID partyId);
}