package gov.bf.ascelc.univers_audits.service.impl;

import gov.bf.ascelc.univers_audits.enums.AuditionStatus;
import gov.bf.ascelc.univers_audits.mapper.DossierDetailsMapper;
import gov.bf.ascelc.univers_audits.model.dto.request.AuditionConductRequest;
import gov.bf.ascelc.univers_audits.model.dto.request.AuditionScheduleRequest;
import gov.bf.ascelc.univers_audits.model.dto.response.AuditionResponse;
import gov.bf.ascelc.univers_audits.model.entity.*;
import gov.bf.ascelc.univers_audits.repository.AuditionRepository;
import gov.bf.ascelc.univers_audits.repository.InvestigationRepository;
import gov.bf.ascelc.univers_audits.repository.TargetedPartyRepository;
import gov.bf.ascelc.univers_audits.repository.WitnessRepository;
import gov.bf.ascelc.univers_audits.service.AuditionService;
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
public class AuditionServiceImpl implements AuditionService {

    private final AuditionRepository       auditionRepository;
    private final InvestigationRepository  investigationRepository;
    private final TargetedPartyRepository  targetedPartyRepository;
    private final WitnessRepository        witnessRepository;
    private final DossierDetailsMapper     mapper;
    private final AgentContextResolver     agentContextResolver;
    private final DossierAccessGuard       accessGuard;

    @Override
    @Transactional
    public AuditionResponse schedule(UUID investigationId, AuditionScheduleRequest request) {
        Investigation investigation = getInvestigationOrThrow(investigationId);

        boolean hasTargetedParty = request.getTargetedPartyId() != null;
        boolean hasWitness = request.getWitnessId() != null;
        if (hasTargetedParty == hasWitness) {
            throw new BusinessException(
                    "Il faut renseigner exactement une personne auditionnée (partie visée OU témoin)");
        }

        Audition.AuditionBuilder<?, ?> builder = Audition.builder()
                .investigation(investigation)
                .intervieweeType(request.getIntervieweeType())
                .scheduledAt(request.getScheduledAt())
                .location(request.getLocation())
                .conductedBy(agentContextResolver.getCurrentAgent());

        if (hasTargetedParty) {
            TargetedParty targetedParty = targetedPartyRepository.findById(request.getTargetedPartyId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Partie visée introuvable : " + request.getTargetedPartyId()));
            builder.targetedParty(targetedParty);
        } else {
            Witness witness = witnessRepository.findById(request.getWitnessId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Témoin introuvable : " + request.getWitnessId()));
            builder.witness(witness);
        }

        Audition saved = auditionRepository.save(builder.build());
        log.info("Audition planifiée — investigation: {}, id: {}", investigationId, saved.getId());
        return mapper.toResponse(saved);
    }

    @Override
    @Transactional
    public AuditionResponse conduct(UUID auditionId, AuditionConductRequest request) {
        Audition audition = getAuditionOrThrow(auditionId);
        if (audition.getStatus() != AuditionStatus.SCHEDULED) {
            throw new BusinessException(
                    "Seule une audition planifiée peut être tenue (statut actuel : " + audition.getStatus() + ")");
        }
        audition.conduct(request.getSummary());
        Audition saved = auditionRepository.save(audition);
        log.info("Audition tenue — id: {}", auditionId);
        return mapper.toResponse(saved);
    }

    @Override
    @Transactional
    public AuditionResponse cancel(UUID auditionId, String reason) {
        Audition audition = getAuditionOrThrow(auditionId);
        if (audition.getStatus() != AuditionStatus.SCHEDULED) {
            throw new BusinessException(
                    "Seule une audition planifiée peut être annulée (statut actuel : " + audition.getStatus() + ")");
        }
        audition.cancel(reason);
        Audition saved = auditionRepository.save(audition);
        log.info("Audition annulée — id: {}, motif: {}", auditionId, reason);
        return mapper.toResponse(saved);
    }

    @Override
    public List<AuditionResponse> findByInvestigationId(UUID investigationId) {
        Investigation investigation = getInvestigationOrThrow(investigationId);

        if (Boolean.TRUE.equals(investigation.getDossier().getIsConfidential())
                && !accessGuard.canSeeConfidential()) {
            return List.of();
        }

        return auditionRepository.findByInvestigationIdOrderByScheduledAtAsc(investigationId)
                .stream()
                .map(mapper::toResponse)
                .toList();
    }

    private Investigation getInvestigationOrThrow(UUID id) {
        return investigationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Investigation introuvable : " + id));
    }

    private Audition getAuditionOrThrow(UUID id) {
        return auditionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Audition introuvable : " + id));
    }
}
