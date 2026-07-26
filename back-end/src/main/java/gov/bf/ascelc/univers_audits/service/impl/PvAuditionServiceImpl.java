package gov.bf.ascelc.univers_audits.service.impl;

import gov.bf.ascelc.univers_audits.mapper.DossierDetailsMapper;
import gov.bf.ascelc.univers_audits.model.dto.request.PvAuditionCreateRequest;
import gov.bf.ascelc.univers_audits.model.dto.request.PvAuditionFinalizeRequest;
import gov.bf.ascelc.univers_audits.model.dto.response.PvAuditionResponse;
import gov.bf.ascelc.univers_audits.model.entity.Audition;
import gov.bf.ascelc.univers_audits.model.entity.Dossier;
import gov.bf.ascelc.univers_audits.model.entity.PVAudition;
import gov.bf.ascelc.univers_audits.repository.AuditionRepository;
import gov.bf.ascelc.univers_audits.repository.PVAuditionRepository;
import gov.bf.ascelc.univers_audits.service.PvAuditionService;
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
public class PvAuditionServiceImpl implements PvAuditionService {

    private final PVAuditionRepository pvAuditionRepository;
    private final AuditionRepository   auditionRepository;
    private final DossierDetailsMapper mapper;
    private final AgentContextResolver agentContextResolver;
    private final DossierAccessGuard   accessGuard;

    @Override
    @Transactional
    public PvAuditionResponse create(UUID auditionId, PvAuditionCreateRequest request) {
        Audition audition = auditionRepository.findById(auditionId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Audition introuvable : " + auditionId));
        accessGuard.checkReadAccess(audition.getInvestigation().getDossier());

        if (pvAuditionRepository.findByAuditionId(auditionId).isPresent()) {
            throw new BusinessException(
                    "Un procès-verbal existe déjà pour cette audition");
        }

        PVAudition pv = PVAudition.builder()
                .audition(audition)
                .content(request.getContent())
                .draftedBy(agentContextResolver.getCurrentAgent())
                .build();

        PVAudition saved = pvAuditionRepository.save(pv);
        log.info("PV d'audition créé — audition: {}, id: {}", auditionId, saved.getId());
        return mapper.toResponse(saved);
    }

    @Override
    @Transactional
    public PvAuditionResponse finalizeSignatures(UUID auditionId, PvAuditionFinalizeRequest request) {
        PVAudition pv = getPvOrThrow(auditionId);
        accessGuard.checkReadAccess(pv.getAudition().getInvestigation().getDossier());

        if (Boolean.TRUE.equals(request.getIntervieweeSigned())
                && Boolean.TRUE.equals(request.getIntervieweeSignatureRefused())) {
            throw new BusinessException(
                    "Un PV ne peut pas être à la fois signé et refusé par la personne auditionnée");
        }
        if (pv.isFinalized()) {
            throw new BusinessException("Ce procès-verbal est déjà finalisé");
        }

        pv.finalizeSignatures(
                Boolean.TRUE.equals(request.getIntervieweeSigned()),
                Boolean.TRUE.equals(request.getIntervieweeSignatureRefused()));

        PVAudition saved = pvAuditionRepository.save(pv);
        log.info("PV d'audition finalisé — audition: {}", auditionId);
        return mapper.toResponse(saved);
    }

    @Override
    public PvAuditionResponse findByAuditionId(UUID auditionId) {
        PVAudition pv = getPvOrThrow(auditionId);
        Dossier dossier = pv.getAudition().getInvestigation().getDossier();
        accessGuard.checkReadAccess(dossier);

        if (Boolean.TRUE.equals(dossier.getIsConfidential())
                && !accessGuard.canSeeConfidential()) {
            throw new BusinessException(
                    "Accès refusé — le procès-verbal d'un dossier confidentiel n'est visible que par les rôles habilités");
        }

        return mapper.toResponse(pv);
    }

    private PVAudition getPvOrThrow(UUID auditionId) {
        return pvAuditionRepository.findByAuditionId(auditionId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Procès-verbal introuvable pour l'audition : " + auditionId));
    }
}
