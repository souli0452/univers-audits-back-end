package gov.bf.ascelc.univers_audits.service;

import gov.bf.ascelc.univers_audits.model.dto.request.InformationPreoccupanteCreateRequest;
import gov.bf.ascelc.univers_audits.model.dto.request.RattacherDossierRequest;
import gov.bf.ascelc.univers_audits.model.dto.response.DossierResponse;
import gov.bf.ascelc.univers_audits.model.dto.response.InformationPreoccupanteResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface InformationPreoccupanteService {

    InformationPreoccupanteResponse create(InformationPreoccupanteCreateRequest request);

    InformationPreoccupanteResponse findById(UUID id);

    Page<InformationPreoccupanteResponse> findAll(Pageable pageable);

    InformationPreoccupanteResponse rattacherDossier(
            UUID informationPreoccupanteId, UUID dossierId, RattacherDossierRequest request);

    DossierResponse declencherAutoSaisine(UUID informationPreoccupanteId, String ipAddress);

    InformationPreoccupanteResponse classerSansSuite(UUID informationPreoccupanteId);
}
