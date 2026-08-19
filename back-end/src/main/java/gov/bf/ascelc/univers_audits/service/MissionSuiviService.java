package gov.bf.ascelc.univers_audits.service;

import gov.bf.ascelc.univers_audits.model.dto.request.MissionSuiviRequest;
import gov.bf.ascelc.univers_audits.model.dto.response.MissionSuiviListResponse;
import gov.bf.ascelc.univers_audits.model.dto.response.MissionSuiviResponse;
import gov.bf.ascelc.univers_audits.model.entity.*;
import gov.bf.ascelc.univers_audits.repository.InvestigationRepository;
import gov.bf.ascelc.univers_audits.repository.MissionSuiviRepository;
import gov.bf.ascelc.univers_audits.repository.PlanActionsRepository;
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
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MissionSuiviService {

    private final MissionSuiviRepository missionSuiviRepository;
    private final PlanActionsRepository planActionsRepository;
    private final InvestigationRepository investigationRepository;
    private final DossierAccessGuard accessGuard;
    private final AgentContextResolver agentContextResolver;
    private final ParametreDelaiService parametreDelaiService;

    @Transactional
    public MissionSuiviListResponse ajouter(UUID investigationId, MissionSuiviRequest request) {
        Investigation investigation = getInvestigationOrThrow(investigationId);
        accessGuard.checkReadAccess(investigation.getDossier());
        checkNotConfidentialMasked(investigation);

        if (investigation.getCgeApprovedAt() == null) {
            throw new BusinessException(
                    "L'enregistrement d'une mission de suivi n'est possible qu'après la décision finale du CGE.");
        }
        PlanActions planActions = planActionsRepository.findByInvestigationId(investigationId)
                .orElseThrow(() -> new BusinessException(
                        "Aucun plan d'actions déposé pour cette investigation — "
                                + "impossible d'enregistrer une mission de suivi."));

        Agent agent = agentContextResolver.getCurrentAgent();
        MissionSuivi mission = MissionSuivi.builder()
                .investigation(investigation)
                .missionDate(request.getMissionDate())
                .conductedBy(agent)
                .objectifs(request.getObjectifs())
                .syntheseRecommandations(request.getSyntheseRecommandations())
                .nouvellesRecommandations(request.getNouvellesRecommandations())
                .submittedAt(Instant.now())
                .build();

        missionSuiviRepository.save(mission);
        log.info("Mission de suivi enregistrée — investigation: {}", investigationId);
        return toListResponse(investigation, planActions);
    }

    public MissionSuiviListResponse lister(UUID investigationId) {
        Investigation investigation = getInvestigationOrThrow(investigationId);
        accessGuard.checkReadAccess(investigation.getDossier());

        if (Boolean.TRUE.equals(investigation.getDossier().getIsConfidential())
                && !accessGuard.canSeeConfidential()) {
            return MissionSuiviListResponse.builder()
                    .investigationId(investigationId)
                    .missionSuiviOverdue(false)
                    .missions(List.of())
                    .build();
        }

        Optional<PlanActions> planActions = planActionsRepository.findByInvestigationId(investigationId);
        return toListResponse(investigation, planActions.orElse(null));
    }

    private void checkNotConfidentialMasked(Investigation investigation) {
        if (Boolean.TRUE.equals(investigation.getDossier().getIsConfidential())
                && !accessGuard.canSeeConfidential()) {
            throw new BusinessException("Accès refusé — ce dossier est confidentiel.");
        }
    }

    private MissionSuiviListResponse toListResponse(Investigation investigation, PlanActions planActions) {
        List<MissionSuivi> missions =
                missionSuiviRepository.findByInvestigationIdOrderByMissionDateDesc(investigation.getId());

        Instant dueAt = planActions != null
                ? resolveDeadline(planActions.getSubmittedAt(), "MISSION_SUIVI_PLAN_ACTIONS")
                : null;
        boolean overdue = missions.isEmpty()
                && dueAt != null
                && Instant.now().isAfter(dueAt);

        return MissionSuiviListResponse.builder()
                .investigationId(investigation.getId())
                .missionSuiviDueAt(dueAt)
                .missionSuiviOverdue(overdue)
                .missions(missions.stream().map(this::toResponse).toList())
                .build();
    }

    private MissionSuiviResponse toResponse(MissionSuivi m) {
        return MissionSuiviResponse.builder()
                .id(m.getId())
                .missionDate(m.getMissionDate())
                .conductedByNom(m.getConductedBy().getNomComplet())
                .objectifs(m.getObjectifs())
                .syntheseRecommandations(m.getSyntheseRecommandations())
                .nouvellesRecommandations(m.getNouvellesRecommandations())
                .submittedAt(m.getSubmittedAt())
                .build();
    }

    private Instant resolveDeadline(Instant from, String delaiCode) {
        try {
            int delaiJours = parametreDelaiService.resolveDelaiJours(delaiCode);
            return from.plusSeconds((long) delaiJours * 24 * 3600);
        } catch (ResourceNotFoundException e) {
            log.warn("Délai {} indisponible — échéance non calculée : {}", delaiCode, e.getMessage());
            return null;
        }
    }

    private Investigation getInvestigationOrThrow(UUID investigationId) {
        return investigationRepository.findById(investigationId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Investigation introuvable : " + investigationId));
    }
}
