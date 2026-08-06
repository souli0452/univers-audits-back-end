package gov.bf.ascelc.univers_audits.service.impl;

import gov.bf.ascelc.univers_audits.mapper.DossierDetailsMapper;
import gov.bf.ascelc.univers_audits.model.dto.request.PvConstatCreateRequest;
import gov.bf.ascelc.univers_audits.model.dto.response.PvConstatResponse;
import gov.bf.ascelc.univers_audits.model.entity.Dossier;
import gov.bf.ascelc.univers_audits.model.entity.PVConstat;
import gov.bf.ascelc.univers_audits.model.entity.VisiteTerrain;
import gov.bf.ascelc.univers_audits.repository.PVConstatRepository;
import gov.bf.ascelc.univers_audits.repository.VisiteTerrainRepository;
import gov.bf.ascelc.univers_audits.service.PvConstatService;
import gov.bf.ascelc.univers_audits.shared.exceptions.BusinessException;
import gov.bf.ascelc.univers_audits.shared.exceptions.ResourceNotFoundException;
import gov.bf.ascelc.univers_audits.shared.utils.AgentContextResolver;
import gov.bf.ascelc.univers_audits.shared.utils.DossierAccessGuard;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PvConstatServiceImpl implements PvConstatService {

    private final PVConstatRepository     pvConstatRepository;
    private final VisiteTerrainRepository visiteTerrainRepository;
    private final DossierDetailsMapper    mapper;
    private final AgentContextResolver    agentContextResolver;
    private final DossierAccessGuard      accessGuard;

    @Override
    @Transactional
    public PvConstatResponse create(UUID visiteId, PvConstatCreateRequest request) {
        VisiteTerrain visite = visiteTerrainRepository.findById(visiteId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Visite terrain introuvable : " + visiteId));
        accessGuard.checkReadAccess(visite.getInvestigation().getDossier());

        if (pvConstatRepository.findByVisiteTerrainId(visiteId).isPresent()) {
            throw new BusinessException(
                    "Un procès-verbal de constat existe déjà pour cette visite");
        }

        PVConstat pv = PVConstat.builder()
                .visiteTerrain(visite)
                .content(request.getContent())
                .draftedBy(agentContextResolver.getCurrentAgent())
                .build();

        PVConstat saved = pvConstatRepository.save(pv);
        log.info("PV de constat créé — visite: {}, id: {}", visiteId, saved.getId());
        return mapper.toResponse(saved);
    }

    @Override
    public PvConstatResponse findByVisiteId(UUID visiteId) {
        PVConstat pv = getPvOrThrow(visiteId);
        Dossier dossier = pv.getVisiteTerrain().getInvestigation().getDossier();
        accessGuard.checkReadAccess(dossier);

        if (Boolean.TRUE.equals(dossier.getIsConfidential())
                && !accessGuard.canSeeConfidential()) {
            throw new BusinessException(
                    "Accès refusé — le procès-verbal d'un dossier confidentiel n'est visible "
                            + "que par les rôles habilités");
        }

        return mapper.toResponse(pv);
    }

    private PVConstat getPvOrThrow(UUID visiteId) {
        return pvConstatRepository.findByVisiteTerrainId(visiteId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Procès-verbal de constat introuvable pour la visite : " + visiteId));
    }
}
