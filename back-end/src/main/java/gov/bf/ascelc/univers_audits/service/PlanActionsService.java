package gov.bf.ascelc.univers_audits.service;

import gov.bf.ascelc.univers_audits.model.dto.request.NoteAvancementRequest;
import gov.bf.ascelc.univers_audits.model.dto.request.PlanActionsRequest;
import gov.bf.ascelc.univers_audits.model.dto.response.NoteAvancementResponse;
import gov.bf.ascelc.univers_audits.model.dto.response.PlanActionsStatusResponse;
import gov.bf.ascelc.univers_audits.model.entity.*;
import gov.bf.ascelc.univers_audits.repository.InvestigationRepository;
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
public class PlanActionsService {

    private final PlanActionsRepository planActionsRepository;
    private final InvestigationRepository investigationRepository;
    private final DossierAccessGuard accessGuard;
    private final AgentContextResolver agentContextResolver;
    private final ParametreDelaiService parametreDelaiService;

    @Transactional
    public PlanActionsStatusResponse creer(UUID investigationId, PlanActionsRequest request) {
        Investigation investigation = getInvestigationOrThrow(investigationId);
        accessGuard.checkReadAccess(investigation.getDossier());
        checkNotConfidentialMasked(investigation);

        if (investigation.getCgeApprovedAt() == null) {
            throw new BusinessException(
                    "Le dépôt du plan d'actions n'est possible qu'après la décision finale du CGE.");
        }
        if (planActionsRepository.findByInvestigationId(investigationId).isPresent()) {
            throw new BusinessException(
                    "Un plan d'actions a déjà été déposé pour ce dossier.");
        }

        Agent agent = agentContextResolver.getCurrentAgent();
        PlanActions planActions = PlanActions.builder()
                .investigation(investigation)
                .entiteControlee(request.getEntiteControlee())
                .contenu(request.getContenu())
                .submittedAt(Instant.now())
                .receivedBy(agent)
                .build();

        PlanActions saved = planActionsRepository.save(planActions);
        log.info("Plan d'actions enregistré — investigation: {}", investigationId);
        return toResponse(investigation, saved);
    }

    public PlanActionsStatusResponse getStatus(UUID investigationId) {
        Investigation investigation = getInvestigationOrThrow(investigationId);
        accessGuard.checkReadAccess(investigation.getDossier());

        if (Boolean.TRUE.equals(investigation.getDossier().getIsConfidential())
                && !accessGuard.canSeeConfidential()) {
            return PlanActionsStatusResponse.builder()
                    .investigationId(investigationId)
                    .exists(false)
                    .planActionsOverdue(false)
                    .avancements(List.of())
                    .build();
        }

        Optional<PlanActions> planActions = planActionsRepository.findByInvestigationId(investigationId);
        return toResponse(investigation, planActions.orElse(null));
    }

    @Transactional
    public PlanActionsStatusResponse ajouterAvancement(UUID investigationId, NoteAvancementRequest request) {
        Investigation investigation = getInvestigationOrThrow(investigationId);
        accessGuard.checkReadAccess(investigation.getDossier());
        checkNotConfidentialMasked(investigation);

        PlanActions planActions = planActionsRepository.findByInvestigationId(investigationId)
                .orElseThrow(() -> new BusinessException(
                        "Aucun plan d'actions déposé pour cette investigation — "
                                + "impossible d'ajouter une note d'avancement."));

        Agent agent = agentContextResolver.getCurrentAgent();
        NoteAvancement note = NoteAvancement.builder()
                .planActions(planActions)
                .noteAt(Instant.now())
                .agent(agent)
                .contenu(request.getContenu())
                .build();
        planActions.getAvancements().add(note);

        PlanActions saved = planActionsRepository.save(planActions);
        log.info("Note d'avancement ajoutée — investigation: {}", investigationId);
        return toResponse(investigation, saved);
    }

    private void checkNotConfidentialMasked(Investigation investigation) {
        if (Boolean.TRUE.equals(investigation.getDossier().getIsConfidential())
                && !accessGuard.canSeeConfidential()) {
            throw new BusinessException("Accès refusé — ce dossier est confidentiel.");
        }
    }

    private PlanActionsStatusResponse toResponse(Investigation investigation, PlanActions planActions) {
        Instant dueAt = investigation.getReportSubmittedAt() != null
                ? resolveDeadline(investigation.getReportSubmittedAt(), "PLAN_ACTIONS_ENTITE_CONTROLEE")
                : null;
        boolean overdue = planActions == null
                && dueAt != null
                && Instant.now().isAfter(dueAt);

        PlanActionsStatusResponse.PlanActionsStatusResponseBuilder builder =
                PlanActionsStatusResponse.builder()
                        .investigationId(investigation.getId())
                        .exists(planActions != null)
                        .planActionsDueAt(dueAt)
                        .planActionsOverdue(overdue)
                        .avancements(List.of());

        if (planActions != null) {
            builder.id(planActions.getId())
                    .entiteControlee(planActions.getEntiteControlee())
                    .contenu(planActions.getContenu())
                    .submittedAt(planActions.getSubmittedAt())
                    .receivedByNom(planActions.getReceivedBy().getNomComplet())
                    .avancements(planActions.getAvancements().stream()
                            .map(this::toAvancementResponse)
                            .toList());
        }

        return builder.build();
    }

    private NoteAvancementResponse toAvancementResponse(NoteAvancement n) {
        return NoteAvancementResponse.builder()
                .id(n.getId())
                .noteAt(n.getNoteAt())
                .agentNom(n.getAgent().getNomComplet())
                .contenu(n.getContenu())
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
