package gov.bf.ascelc.univers_audits.service.impl;

import gov.bf.ascelc.univers_audits.mapper.DossierDetailsMapper;
import gov.bf.ascelc.univers_audits.model.dto.request.ObservationRequest;
import gov.bf.ascelc.univers_audits.model.dto.response.ObservationResponse;
import gov.bf.ascelc.univers_audits.model.entity.Agent;
import gov.bf.ascelc.univers_audits.model.entity.Dossier;
import gov.bf.ascelc.univers_audits.model.entity.Observation;
import gov.bf.ascelc.univers_audits.repository.DossierRepository;
import gov.bf.ascelc.univers_audits.repository.ObservationRepository;
import gov.bf.ascelc.univers_audits.service.ObservationService;
import gov.bf.ascelc.univers_audits.shared.exceptions.BusinessException;
import gov.bf.ascelc.univers_audits.shared.exceptions.ResourceNotFoundException;
import gov.bf.ascelc.univers_audits.shared.utils.AgentContextResolver;
import gov.bf.ascelc.univers_audits.shared.utils.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ObservationServiceImpl implements ObservationService {

    private final ObservationRepository observationRepository;
    private final DossierRepository     dossierRepository;
    private final DossierDetailsMapper  detailsMapper;
    private final SecurityUtils         securityUtils;
    private final AgentContextResolver  agentContextResolver;

    @Override
    public List<ObservationResponse> findByDossierId(UUID dossierId) {
        getDossierOrThrow(dossierId);


        boolean canSeeConfidential =
                securityUtils.hasRole("CGE")
                        || securityUtils.hasRole("CGEA")
                        || securityUtils.hasRole("CONSEILLER_JURIDIQUE");

        if (canSeeConfidential) {
            return observationRepository
                    .findByDossierIdOrderByCreatedAtAsc(dossierId)
                    .stream()
                    .map(detailsMapper::toResponse)
                    .collect(Collectors.toList());
        }

        return observationRepository
                .findByDossierIdAndConfidentialFalse(dossierId)
                .stream()
                .map(detailsMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public ObservationResponse create(UUID dossierId,
                                      ObservationRequest request) {
        Dossier dossier = getDossierOrThrow(dossierId);

        if (dossier.isClosed()) {
            throw new BusinessException(
                    "Impossible d'ajouter une observation à un dossier clôturé");
        }

        Agent agent = agentContextResolver.getCurrentAgent();

        Observation obs = Observation.builder()
                .dossier(dossier)
                .type(request.getType())
                .content(request.getContent())
                .confidential(Boolean.TRUE.equals(request.getConfidential()))
                .author(agent)
                .authorFullName(agent.getNomComplet())
                .statusSnapshot(dossier.getStatus())
                .build();

        Observation saved = observationRepository.save(obs);
        log.info("Observation ajoutée — dossier: {}, type: {}, auteur: {}",
                dossierId, request.getType(), agent.getMatricule());
        return detailsMapper.toResponse(saved);
    }


    private Dossier getDossierOrThrow(UUID dossierId) {
        return dossierRepository.findById(dossierId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Dossier introuvable : " + dossierId));
    }
}