package gov.bf.ascelc.univers_audits.service;

import gov.bf.ascelc.univers_audits.model.dto.request.EtudeOpportuniteRequest;
import gov.bf.ascelc.univers_audits.model.dto.response.EtudeOpportuniteResponse;

import java.util.UUID;

public interface EtudeOpportuniteService {

    EtudeOpportuniteResponse findByDossierId(UUID dossierId);

    EtudeOpportuniteResponse upsert(UUID dossierId, EtudeOpportuniteRequest request);
}
