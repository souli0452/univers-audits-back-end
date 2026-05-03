package gov.bf.ascelc.univers_audits.service;

import gov.bf.ascelc.univers_audits.model.dto.request.ObservationRequest;
import gov.bf.ascelc.univers_audits.model.dto.response.ObservationResponse;

import java.util.List;
import java.util.UUID;

public interface ObservationService {

    List<ObservationResponse> findByDossierId(UUID dossierId);

    ObservationResponse create(UUID dossierId, ObservationRequest request);
}