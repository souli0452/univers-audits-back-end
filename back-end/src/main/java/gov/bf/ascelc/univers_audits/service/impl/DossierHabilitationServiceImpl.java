package gov.bf.ascelc.univers_audits.service.impl;

import gov.bf.ascelc.univers_audits.enums.HabilitationSource;
import gov.bf.ascelc.univers_audits.mapper.DossierHabilitationMapper;
import gov.bf.ascelc.univers_audits.model.dto.request.HabilitationGrantRequest;
import gov.bf.ascelc.univers_audits.model.dto.response.DossierHabilitationResponse;
import gov.bf.ascelc.univers_audits.model.entity.Agent;
import gov.bf.ascelc.univers_audits.model.entity.Dossier;
import gov.bf.ascelc.univers_audits.model.entity.DossierHabilitation;
import gov.bf.ascelc.univers_audits.repository.AgentRepository;
import gov.bf.ascelc.univers_audits.repository.DossierHabilitationRepository;
import gov.bf.ascelc.univers_audits.service.DossierHabilitationService;
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
public class DossierHabilitationServiceImpl implements DossierHabilitationService {

    private final DossierHabilitationRepository habilitationRepository;
    private final AgentRepository               agentRepository;
    private final DossierAccessGuard            accessGuard;
    private final AgentContextResolver          agentContextResolver;
    private final DossierHabilitationMapper     mapper;

    @Override
    @Transactional
    public void grant(Dossier dossier, Agent agent, HabilitationSource source,
                       Agent grantedBy, String reason) {
        boolean alreadyActive = habilitationRepository
                .existsByDossierIdAndAgentIdAndSourceAndRevokedAtIsNull(
                        dossier.getId(), agent.getId(), source);
        if (alreadyActive) {
            return;
        }
        DossierHabilitation habilitation = DossierHabilitation.builder()
                .dossier(dossier)
                .agent(agent)
                .source(source)
                .grantedBy(grantedBy)
                .reason(reason)
                .build();
        habilitationRepository.save(habilitation);
        log.info("[Habilitation] Octroyée — dossier: {}, agent: {}, source: {}",
                dossier.getId(), agent.getId(), source);
    }

    @Override
    @Transactional
    public void revokeBySource(Dossier dossier, Agent agent, HabilitationSource source) {
        habilitationRepository
                .findFirstByDossierIdAndAgentIdAndSourceAndRevokedAtIsNullOrderByCreatedAtDesc(
                        dossier.getId(), agent.getId(), source)
                .ifPresentOrElse(
                        h -> {
                            h.revoke(null, "Retrait automatique — source " + source);
                            habilitationRepository.save(h);
                            log.info("[Habilitation] Révoquée — dossier: {}, agent: {}, source: {}",
                                    dossier.getId(), agent.getId(), source);
                        },
                        () -> log.warn("[Habilitation] Aucune habilitation active à révoquer — "
                                        + "dossier: {}, agent: {}, source: {}",
                                dossier.getId(), agent.getId(), source));
    }

    @Override
    @Transactional
    public DossierHabilitationResponse grantManual(UUID dossierId, HabilitationGrantRequest request) {
        Dossier dossier = accessGuard.getDossierOrThrow(dossierId);
        accessGuard.checkReadAccess(dossier);

        Agent agent = agentRepository.findById(request.getAgentId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Agent introuvable : " + request.getAgentId()));

        boolean alreadyActive = habilitationRepository
                .existsByDossierIdAndAgentIdAndSourceAndRevokedAtIsNull(
                        dossierId, agent.getId(), HabilitationSource.MANUAL);
        if (alreadyActive) {
            throw new BusinessException(
                    "Cet agent a déjà un accès manuel actif à ce dossier");
        }

        Agent currentAgent = agentContextResolver.getCurrentAgent();
        DossierHabilitation habilitation = DossierHabilitation.builder()
                .dossier(dossier)
                .agent(agent)
                .source(HabilitationSource.MANUAL)
                .grantedBy(currentAgent)
                .reason(request.getReason())
                .build();

        DossierHabilitation saved = habilitationRepository.save(habilitation);
        log.info("[Habilitation] Octroi manuel — dossier: {}, agent: {}, par: {}",
                dossierId, agent.getId(), currentAgent.getId());
        return mapper.toResponse(saved);
    }

    @Override
    @Transactional
    public void revokeManual(UUID dossierId, UUID agentId, String reason) {
        accessGuard.checkReadAccess(accessGuard.getDossierOrThrow(dossierId));

        List<DossierHabilitation> active = habilitationRepository
                .findByDossierIdAndAgentIdAndRevokedAtIsNull(dossierId, agentId);
        if (active.isEmpty()) {
            throw new BusinessException("Cet agent n'a aucun accès actif à ce dossier");
        }

        Agent currentAgent = agentContextResolver.getCurrentAgent();
        active.forEach(h -> h.revoke(currentAgent, reason));
        habilitationRepository.saveAll(active);
        log.info("[Habilitation] Révocation manuelle — dossier: {}, agent: {}, par: {}",
                dossierId, agentId, currentAgent.getId());
    }

    @Override
    public List<DossierHabilitationResponse> findActiveByDossier(UUID dossierId) {
        accessGuard.checkReadAccess(accessGuard.getDossierOrThrow(dossierId));
        return habilitationRepository.findByDossierIdAndRevokedAtIsNull(dossierId)
                .stream()
                .map(mapper::toResponse)
                .toList();
    }
}
