package gov.bf.ascelc.univers_audits.service.impl;

import gov.bf.ascelc.univers_audits.enums.VisiteStatus;
import gov.bf.ascelc.univers_audits.mapper.DossierDetailsMapper;
import gov.bf.ascelc.univers_audits.model.dto.request.VisiteTerrainConductRequest;
import gov.bf.ascelc.univers_audits.model.dto.request.VisiteTerrainScheduleRequest;
import gov.bf.ascelc.univers_audits.model.dto.response.VisiteTerrainResponse;
import gov.bf.ascelc.univers_audits.model.entity.Investigation;
import gov.bf.ascelc.univers_audits.model.entity.VisiteTerrain;
import gov.bf.ascelc.univers_audits.repository.InvestigationRepository;
import gov.bf.ascelc.univers_audits.repository.VisiteTerrainRepository;
import gov.bf.ascelc.univers_audits.service.VisiteTerrainService;
import gov.bf.ascelc.univers_audits.shared.exceptions.BusinessException;
import gov.bf.ascelc.univers_audits.shared.exceptions.ResourceNotFoundException;
import gov.bf.ascelc.univers_audits.shared.utils.AgentContextResolver;
import gov.bf.ascelc.univers_audits.shared.utils.DossierAccessGuard;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class VisiteTerrainServiceImpl implements VisiteTerrainService {

    private final VisiteTerrainRepository visiteTerrainRepository;
    private final InvestigationRepository investigationRepository;
    private final DossierDetailsMapper    mapper;
    private final AgentContextResolver    agentContextResolver;
    private final DossierAccessGuard      accessGuard;

    @Override
    @Transactional
    public VisiteTerrainResponse schedule(UUID investigationId, VisiteTerrainScheduleRequest request) {
        Investigation investigation = getInvestigationOrThrow(investigationId);
        accessGuard.checkReadAccess(investigation.getDossier());

        VisiteTerrain visite = VisiteTerrain.builder()
                .investigation(investigation)
                .location(request.getLocation())
                .scheduledAt(request.getScheduledAt())
                .plannedBy(agentContextResolver.getCurrentAgent())
                .build();

        VisiteTerrain saved = visiteTerrainRepository.save(visite);
        log.info("Visite terrain planifiée — investigation: {}, id: {}", investigationId, saved.getId());
        return mapper.toResponse(saved);
    }

    @Override
    @Transactional
    public VisiteTerrainResponse conduct(UUID visiteId, VisiteTerrainConductRequest request) {
        VisiteTerrain visite = getVisiteOrThrow(visiteId);
        accessGuard.checkReadAccess(visite.getInvestigation().getDossier());
        if (visite.getStatus() != VisiteStatus.SCHEDULED) {
            throw new BusinessException(
                    "Seule une visite planifiée peut être tenue (statut actuel : " + visite.getStatus() + ")");
        }
        visite.conduct(request.getSummary());
        VisiteTerrain saved = visiteTerrainRepository.save(visite);
        log.info("Visite terrain tenue — id: {}", visiteId);
        return mapper.toResponse(saved);
    }

    @Override
    @Transactional
    public VisiteTerrainResponse cancel(UUID visiteId, String reason) {
        VisiteTerrain visite = getVisiteOrThrow(visiteId);
        accessGuard.checkReadAccess(visite.getInvestigation().getDossier());
        if (visite.getStatus() != VisiteStatus.SCHEDULED) {
            throw new BusinessException(
                    "Seule une visite planifiée peut être annulée (statut actuel : " + visite.getStatus() + ")");
        }
        visite.cancel(reason);
        VisiteTerrain saved = visiteTerrainRepository.save(visite);
        log.info("Visite terrain annulée — id: {}, motif: {}", visiteId, reason);
        return mapper.toResponse(saved);
    }

    @Override
    @Transactional
    public VisiteTerrainResponse markCarence(UUID visiteId, String reason) {
        VisiteTerrain visite = getVisiteOrThrow(visiteId);
        accessGuard.checkReadAccess(visite.getInvestigation().getDossier());
        if (visite.getStatus() != VisiteStatus.SCHEDULED) {
            throw new BusinessException(
                    "Seule une visite planifiée peut faire l'objet d'un constat de carence "
                            + "(statut actuel : " + visite.getStatus() + ")");
        }
        visite.markCarence(reason);
        VisiteTerrain saved = visiteTerrainRepository.save(visite);
        log.info("Visite terrain — constat de carence — id: {}, motif: {}", visiteId, reason);
        return mapper.toResponse(saved);
    }

    @Override
    public List<VisiteTerrainResponse> findByInvestigationId(UUID investigationId) {
        Investigation investigation = getInvestigationOrThrow(investigationId);
        accessGuard.checkReadAccess(investigation.getDossier());

        if (Boolean.TRUE.equals(investigation.getDossier().getIsConfidential())
                && !accessGuard.canSeeConfidential()) {
            return List.of();
        }

        return visiteTerrainRepository.findByInvestigationIdOrderByScheduledAtAsc(investigationId)
                .stream()
                .map(mapper::toResponse)
                .toList();
    }

    private Investigation getInvestigationOrThrow(UUID id) {
        return investigationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Investigation introuvable : " + id));
    }

    private VisiteTerrain getVisiteOrThrow(UUID id) {
        return visiteTerrainRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Visite terrain introuvable : " + id));
    }
}
