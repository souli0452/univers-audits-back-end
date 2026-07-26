package gov.bf.ascelc.univers_audits.service;

import gov.bf.ascelc.univers_audits.model.dto.request.DemandeDocumentsCreateRequest;
import gov.bf.ascelc.univers_audits.model.dto.response.DemandeDocumentsResponse;

import java.util.List;
import java.util.UUID;

public interface DemandeDocumentsService {

    DemandeDocumentsResponse create(UUID investigationId, DemandeDocumentsCreateRequest request);

    DemandeDocumentsResponse markReceived(UUID id);

    DemandeDocumentsResponse escalate(UUID id);

    List<DemandeDocumentsResponse> findByInvestigationId(UUID investigationId);
}
