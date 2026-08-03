package gov.bf.ascelc.univers_audits.service;

import gov.bf.ascelc.univers_audits.model.dto.request.AddDossierToSeanceRequest;
import gov.bf.ascelc.univers_audits.model.dto.request.RecommandationCtadpRequest;
import gov.bf.ascelc.univers_audits.model.dto.request.SeanceCtadpCreateRequest;
import gov.bf.ascelc.univers_audits.model.dto.request.TenirSeanceRequest;
import gov.bf.ascelc.univers_audits.model.dto.response.SeanceCtadpResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface SeanceCtadpService {

    SeanceCtadpResponse create(SeanceCtadpCreateRequest request);

    SeanceCtadpResponse findById(UUID id);

    Page<SeanceCtadpResponse> findAll(Pageable pageable);

    SeanceCtadpResponse addDossier(UUID seanceId, AddDossierToSeanceRequest request);

    SeanceCtadpResponse recordRecommandation(
            UUID seanceId, UUID dossierId, RecommandationCtadpRequest request);

    SeanceCtadpResponse tenir(UUID seanceId, TenirSeanceRequest request);
}
