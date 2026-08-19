package gov.bf.ascelc.univers_audits.service;

import gov.bf.ascelc.univers_audits.model.dto.request.SuiviProcedurePenaleRequest;
import gov.bf.ascelc.univers_audits.model.dto.response.SuiviProcedurePenaleListResponse;
import gov.bf.ascelc.univers_audits.model.dto.response.SuiviProcedurePenaleResponse;
import gov.bf.ascelc.univers_audits.model.entity.*;
import gov.bf.ascelc.univers_audits.repository.InvestigationRepository;
import gov.bf.ascelc.univers_audits.repository.SuiviProcedurePenaleRepository;
import gov.bf.ascelc.univers_audits.shared.exceptions.BusinessException;
import gov.bf.ascelc.univers_audits.shared.exceptions.ResourceNotFoundException;
import gov.bf.ascelc.univers_audits.shared.utils.AgentContextResolver;
import gov.bf.ascelc.univers_audits.shared.utils.DossierAccessGuard;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SuiviProcedurePenaleService {

    private final SuiviProcedurePenaleRepository suiviProcedurePenaleRepository;
    private final InvestigationRepository investigationRepository;
    private final DossierAccessGuard accessGuard;
    private final AgentContextResolver agentContextResolver;

    @Transactional
    public SuiviProcedurePenaleListResponse ajouter(UUID investigationId, SuiviProcedurePenaleRequest request) {
        Investigation investigation = getInvestigationOrThrow(investigationId);
        accessGuard.checkReadAccess(investigation.getDossier());

        if (investigation.getCgeApprovedAt() == null) {
            throw new BusinessException(
                    "L'enregistrement d'un suivi de procédure pénale n'est possible qu'après la décision finale du CGE.");
        }

        Agent agent = agentContextResolver.getCurrentAgent();
        SuiviProcedurePenale suivi = SuiviProcedurePenale.builder()
                .investigation(investigation)
                .phaseAt(request.getPhaseAt())
                .phase(request.getPhase())
                .commentaire(request.getCommentaire())
                .agent(agent)
                .submittedAt(Instant.now())
                .build();

        suiviProcedurePenaleRepository.save(suivi);
        log.info("Suivi de procédure pénale enregistré — investigation: {}", investigationId);
        return toListResponse(investigation);
    }

    public SuiviProcedurePenaleListResponse lister(UUID investigationId) {
        Investigation investigation = getInvestigationOrThrow(investigationId);
        accessGuard.checkReadAccess(investigation.getDossier());

        if (Boolean.TRUE.equals(investigation.getDossier().getIsConfidential())
                && !accessGuard.canSeeConfidential()) {
            return SuiviProcedurePenaleListResponse.builder()
                    .investigationId(investigationId)
                    .suivis(List.of())
                    .build();
        }

        return toListResponse(investigation);
    }

    private SuiviProcedurePenaleListResponse toListResponse(Investigation investigation) {
        List<SuiviProcedurePenale> suivis =
                suiviProcedurePenaleRepository.findByInvestigationIdOrderByPhaseAtDesc(investigation.getId());

        return SuiviProcedurePenaleListResponse.builder()
                .investigationId(investigation.getId())
                .suivis(suivis.stream().map(this::toResponse).toList())
                .build();
    }

    private SuiviProcedurePenaleResponse toResponse(SuiviProcedurePenale s) {
        return SuiviProcedurePenaleResponse.builder()
                .id(s.getId())
                .phaseAt(s.getPhaseAt())
                .phase(s.getPhase())
                .commentaire(s.getCommentaire())
                .agentNom(s.getAgent().getNomComplet())
                .submittedAt(s.getSubmittedAt())
                .build();
    }

    private Investigation getInvestigationOrThrow(UUID investigationId) {
        return investigationRepository.findById(investigationId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Investigation introuvable : " + investigationId));
    }
}
