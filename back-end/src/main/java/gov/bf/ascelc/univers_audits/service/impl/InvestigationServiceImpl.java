package gov.bf.ascelc.univers_audits.service.impl;

import gov.bf.ascelc.univers_audits.enums.DossierStatus;
import gov.bf.ascelc.univers_audits.enums.InvestigationOutcome;
import gov.bf.ascelc.univers_audits.enums.InvestigationStatus;
import gov.bf.ascelc.univers_audits.enums.ObservationType;
import gov.bf.ascelc.univers_audits.shared.exceptions.BusinessException;
import gov.bf.ascelc.univers_audits.shared.exceptions.ResourceNotFoundException;
import gov.bf.ascelc.univers_audits.mapper.InvestigationMapper;
import gov.bf.ascelc.univers_audits.model.dto.request.*;
import gov.bf.ascelc.univers_audits.model.dto.response.InvestigationResponse;
import gov.bf.ascelc.univers_audits.model.entity.*;
import gov.bf.ascelc.univers_audits.repository.*;
import gov.bf.ascelc.univers_audits.service.InvestigationService;
import gov.bf.ascelc.univers_audits.shared.utils.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InvestigationServiceImpl implements InvestigationService {

    private final InvestigationRepository       investigationRepository;
    private final InvestigationMemberRepository memberRepository;
    private final DossierRepository             dossierRepository;
    private final AgentRepository               agentRepository;
    private final ObservationRepository         observationRepository;
    private final StatusHistoryRepository       statusHistoryRepository;
    private final InvestigationMapper           investigationMapper;
    private final SecurityUtils                 securityUtils;

    // ── Lecture ───────────────────────────────────────────────

    @Override
    public InvestigationResponse findById(UUID id) {
        return investigationMapper.toResponse(getInvestigationOrThrow(id));
    }

    @Override
    public InvestigationResponse findByDossierId(UUID dossierId) {
        Investigation inv = investigationRepository
                .findByDossierId(dossierId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Aucune investigation pour ce dossier"));
        return investigationMapper.toResponse(inv);
    }

    @Override
    public Page<InvestigationResponse> findAll(Pageable pageable) {
        return investigationRepository
                .findAllWithMembers(pageable)
                .map(investigationMapper::toResponse);
    }

    @Override
    public Page<InvestigationResponse> findOverdue(Pageable pageable) {
        List<Investigation> overdueList =
                investigationRepository.findOverdue(Instant.now());

        int start = (int) pageable.getOffset();
        int end   = Math.min(start + pageable.getPageSize(), overdueList.size());

        List<InvestigationResponse> pageContent =
                (start >= overdueList.size()
                        ? List.<Investigation>of()
                        : overdueList.subList(start, end))
                        .stream()
                        .map(investigationMapper::toResponse)
                        .toList();

        return new PageImpl<>(pageContent, pageable, overdueList.size());
    }

    /**
     * Filtre les investigations dont la date de démarrage est dans la période.
     * Utilisé par le rapport d'état des investigations.
     */
    @Override
    @Transactional(readOnly = true)
    public Page<InvestigationResponse> findByPeriod(
            Instant start, Instant end, Pageable pageable) {
        return investigationRepository
                .findByStartDateBetween(start, end, pageable)
                .map(investigationMapper::toResponse);
    }

    // ── Workflow ──────────────────────────────────────────────

    @Override
    @Transactional
    public InvestigationResponse open(
            UUID dossierId,
            InvestigationCreateRequest request,
            String ipAddress) {

        Dossier dossier = dossierRepository.findById(dossierId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Dossier introuvable : " + dossierId));

        if (dossier.getStatus() != DossierStatus.RECEVABLE) {
            throw new BusinessException(
                    "Une investigation ne peut être ouverte "
                            + "que sur un dossier déclaré recevable. "
                            + "Statut actuel : " + dossier.getStatus());
        }

        if (investigationRepository.existsByDossierId(dossierId)) {
            throw new BusinessException(
                    "Une investigation existe déjà pour ce dossier");
        }

        Agent cgea = getCurrentAgent();

        Investigation investigation = Investigation.builder()
                .dossier(dossier)
                .cgea(cgea)
                .status(InvestigationStatus.INITIATED)
                .plannedDurationDays(
                        request.getPlannedDurationDays() != null
                                ? request.getPlannedDurationDays()
                                : 90)
                .build();

        Investigation saved = investigationRepository.save(investigation);

        dossier.setStatus(DossierStatus.EN_INVESTIGATION);
        dossierRepository.save(dossier);

        recordDossierStatusChange(dossier,
                DossierStatus.RECEVABLE,
                DossierStatus.EN_INVESTIGATION,
                "Investigation ouverte par le CGEA",
                cgea, ipAddress);

        addObservation(dossier,
                ObservationType.INTERNAL_NOTE,
                "Investigation ouverte. Durée prévue : "
                        + saved.getPlannedDurationDays() + " jours.",
                true, cgea);

        log.info("Investigation ouverte — dossier: {}", dossier.getNumber());
        return investigationMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public InvestigationResponse start(UUID investigationId, String ipAddress) {

        Investigation inv = getInvestigationOrThrow(investigationId);

        long memberCount = memberRepository
                .countByInvestigationIdAndActiveTrue(investigationId);

        if (memberCount == 0) {
            throw new BusinessException(
                    "L'équipe d'investigation doit avoir "
                            + "au moins un membre avant le démarrage");
        }

        inv.start();
        Investigation saved = investigationRepository.save(inv);

        addObservation(inv.getDossier(),
                ObservationType.INTERNAL_NOTE,
                "Investigation démarrée. Date de fin prévue : "
                        + saved.getPlannedEndDate(),
                true, getCurrentAgent());

        log.info("Investigation {} démarrée — fin prévue: {}",
                investigationId, saved.getPlannedEndDate());
        return investigationMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public InvestigationResponse suspend(UUID investigationId,
                                         String reason,
                                         String ipAddress) {

        Investigation inv = getInvestigationOrThrow(investigationId);

        if (inv.getStatus() != InvestigationStatus.IN_PROGRESS) {
            throw new BusinessException(
                    "Seule une investigation en cours peut être suspendue");
        }
        if (reason == null || reason.isBlank()) {
            throw new BusinessException("Le motif de suspension est obligatoire");
        }

        inv.suspend(reason);
        Investigation saved = investigationRepository.save(inv);

        addObservation(inv.getDossier(),
                ObservationType.INTERNAL_NOTE,
                "Investigation suspendue. Motif : " + reason,
                true, getCurrentAgent());

        return investigationMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public InvestigationResponse resume(UUID investigationId,
                                        String reason,
                                        String ipAddress) {

        Investigation inv = getInvestigationOrThrow(investigationId);

        if (inv.getStatus() != InvestigationStatus.SUSPENDED) {
            throw new BusinessException(
                    "Seule une investigation suspendue peut être reprise");
        }

        inv.setStatus(InvestigationStatus.IN_PROGRESS);
        inv.setSuspensionReason(null);
        Investigation saved = investigationRepository.save(inv);

        addObservation(inv.getDossier(),
                ObservationType.INTERNAL_NOTE,
                "Investigation reprise. " + (reason != null ? reason : ""),
                true, getCurrentAgent());

        return investigationMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public InvestigationResponse extendDeadline(
            UUID investigationId,
            ExtendDeadlineRequest request,
            String ipAddress) {

        Investigation inv = getInvestigationOrThrow(investigationId);
        Agent cgea = getCurrentAgent();

        if (inv.getStatus() != InvestigationStatus.IN_PROGRESS
                && inv.getStatus() != InvestigationStatus.SUSPENDED) {
            throw new BusinessException(
                    "La prolongation n'est possible que pour une investigation "
                            + "en cours ou suspendue.");
        }

        if (request.getReason() == null || request.getReason().isBlank()) {
            throw new BusinessException(
                    "Le motif de prolongation est obligatoire (§C.2.4.3).");
        }

        if (request.getNewDeadline() == null) {
            throw new BusinessException("La nouvelle échéance est obligatoire.");
        }

        Instant currentDeadline = inv.getExtendedDeadline() != null
                ? inv.getExtendedDeadline()
                : inv.getPlannedEndDate();

        if (currentDeadline != null
                && !request.getNewDeadline().isAfter(currentDeadline)) {
            throw new BusinessException(
                    "La nouvelle échéance doit être postérieure à l'échéance actuelle ("
                            + currentDeadline + ").");
        }

        inv.extendDeadline(request.getNewDeadline(), request.getReason(), cgea);
        Investigation saved = investigationRepository.save(inv);

        addObservation(inv.getDossier(),
                ObservationType.INTERNAL_NOTE,
                "Délai d'investigation prolongé jusqu'au "
                        + request.getNewDeadline()
                        + " — Accord hiérarchique (DEI, CGEA, CGE) obtenu. "
                        + "Motif : " + request.getReason(),
                true, cgea);

        log.info("Investigation {} prolongée jusqu'au {}",
                investigationId, request.getNewDeadline());
        return investigationMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public InvestigationResponse submitReport(
            UUID investigationId,
            InvestigationUpdateRequest request,
            String ipAddress) {

        Investigation inv = getInvestigationOrThrow(investigationId);

        if (inv.getStatus() != InvestigationStatus.IN_PROGRESS) {
            throw new BusinessException(
                    "Le rapport ne peut être soumis "
                            + "que pour une investigation en cours");
        }

        inv.setFinalReport(request.getFinalReport());
        inv.setConclusions(request.getConclusions());
        inv.setRecommendations(request.getRecommendations());
        inv.setOutcome(request.getOutcome());
        inv.complete();

        Dossier dossier = inv.getDossier();
        dossier.setStatus(DossierStatus.RAPPORT_PRODUIT);
        dossierRepository.save(dossier);

        recordDossierStatusChange(dossier,
                DossierStatus.EN_INVESTIGATION,
                DossierStatus.RAPPORT_PRODUIT,
                "Rapport d'investigation soumis",
                getCurrentAgent(), ipAddress);

        Investigation saved = investigationRepository.save(inv);

        addObservation(dossier,
                ObservationType.FIELD_FINDING,
                "Rapport final soumis. Conclusions : " + request.getConclusions(),
                true, getCurrentAgent());

        log.info("Rapport soumis — investigation: {}", investigationId);
        return investigationMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public InvestigationResponse approveDei(UUID investigationId,
                                            String ipAddress) {
        Investigation inv = getInvestigationOrThrow(investigationId);

        if (inv.getStatus() != InvestigationStatus.COMPLETED) {
            throw new BusinessException(
                    "L'approbation DEI n'est possible qu'après soumission du rapport.");
        }

        Agent agent = getCurrentAgent();
        inv.setDeiApprovedAt(Instant.now());
        inv.setDeiApprovedBy(agent);
        Investigation saved = investigationRepository.save(inv);

        addObservation(inv.getDossier(),
                ObservationType.INTERNAL_NOTE,
                "Rapport approuvé par le DEI (délai légal : 15 jours ouvrables).",
                true, agent);

        log.info("DEI approuvé — investigation: {}", investigationId);
        return investigationMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public InvestigationResponse approveLegalAdvisor(
            UUID investigationId, String ipAddress) {

        Investigation inv = getInvestigationOrThrow(investigationId);

        if (inv.getDeiApprovedAt() == null) {
            throw new BusinessException(
                    "Le rapport doit d'abord être approuvé par le DEI.");
        }

        Agent agent = getCurrentAgent();
        inv.setLegalAdvisorApprovedAt(Instant.now());
        inv.setLegalAdvisorApprovedBy(agent);
        Investigation saved = investigationRepository.save(inv);

        addObservation(inv.getDossier(),
                ObservationType.ADMISSIBILITY_ANALYSIS,
                "Rapport approuvé par le Conseiller Juridique "
                        + "(délai légal : 10 jours ouvrables).",
                true, agent);

        log.info("Conseiller juridique approuvé — investigation: {}", investigationId);
        return investigationMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public InvestigationResponse approveCge(UUID investigationId,
                                            String reason,
                                            String ipAddress) {

        Investigation inv = getInvestigationOrThrow(investigationId);

        if (inv.getDeiApprovedAt() == null) {
            throw new BusinessException(
                    "Le rapport doit être approuvé par le DEI avant la décision CGE.");
        }
        if (inv.getLegalAdvisorApprovedAt() == null) {
            throw new BusinessException(
                    "Le rapport doit être approuvé par le Conseiller Juridique "
                            + "avant la décision CGE.");
        }

        Agent cge = getCurrentAgent();

        inv.setCgeApprovedAt(Instant.now());
        inv.setCgeApprovedBy(cge);
        inv.setStatus(InvestigationStatus.ARCHIVED);

        Dossier       dossier        = inv.getDossier();
        DossierStatus previousStatus = dossier.getStatus();
        DossierStatus newDossierStatus;
        String        transitionReason;
        String        observationContent;

        if (inv.getOutcome() == InvestigationOutcome.ARCHIVED) {
            newDossierStatus   = DossierStatus.CLASSE;
            dossier.setClosingDate(Instant.now());
            transitionReason   = "Investigation classée sans suite par le CGE. " + reason;
            observationContent = "Décision finale CGE — Classé sans suite "
                    + "(présomptions non confirmées). " + reason;
            log.info("[approveCge] Dossier {} → CLASSE (outcome: ARCHIVED)",
                    dossier.getNumber());
        } else {
            newDossierStatus   = DossierStatus.DECISION_RENDUE;
            transitionReason   = "Décision finale CGE rendue. Outcome : "
                    + inv.getOutcome() + ". " + reason;
            observationContent = "Décision finale rendue par le CGE. Outcome : "
                    + inv.getOutcome().name() + ". " + reason;
            log.info("[approveCge] Dossier {} → DECISION_RENDUE (outcome: {})",
                    dossier.getNumber(), inv.getOutcome());
        }

        dossier.setStatus(newDossierStatus);
        dossierRepository.save(dossier);

        recordDossierStatusChange(dossier, previousStatus, newDossierStatus,
                transitionReason, cge, ipAddress);

        Investigation saved = investigationRepository.save(inv);

        addObservation(dossier, ObservationType.CGE_DECISION,
                observationContent, true, cge);

        log.info("Décision CGE finalisée — dossier: {} → {}",
                dossier.getNumber(), newDossierStatus);
        return investigationMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public InvestigationResponse addMember(
            UUID investigationId,
            AddMemberRequest request,
            String ipAddress) {

        Investigation inv = getInvestigationOrThrow(investigationId);

        if (inv.getStatus() == InvestigationStatus.COMPLETED
                || inv.getStatus() == InvestigationStatus.ARCHIVED) {
            throw new BusinessException(
                    "Impossible d'ajouter un membre à une investigation terminée");
        }

        if (memberRepository.existsByInvestigationIdAndAgentIdAndActiveTrue(
                investigationId, request.getAgentId())) {
            throw new BusinessException(
                    "Cet agent est déjà membre de cette investigation");
        }

        Agent agent = agentRepository.findById(request.getAgentId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Agent introuvable : " + request.getAgentId()));

        Agent currentAgent = getCurrentAgent();

        InvestigationMember member = InvestigationMember.builder()
                .investigation(inv)
                .agent(agent)
                .teamRole(request.getTeamRole())
                .assignedBy(currentAgent.getKeycloakId())
                .active(true)
                .build();

        memberRepository.save(member);

        addObservation(inv.getDossier(),
                ObservationType.INTERNAL_NOTE,
                "Membre ajouté à l'équipe : "
                        + agent.getNomComplet()
                        + " (" + request.getTeamRole() + ")",
                true, currentAgent);

        return investigationMapper.toResponse(
                getInvestigationOrThrow(investigationId));
    }

    @Override
    @Transactional
    public InvestigationResponse removeMember(
            UUID investigationId,
            UUID agentId,
            String ipAddress) {

        Investigation inv = getInvestigationOrThrow(investigationId);
        Agent currentAgent = getCurrentAgent();

        var members = memberRepository
                .findByInvestigationIdAndActiveTrue(investigationId);

        InvestigationMember member = members.stream()
                .filter(m -> m.getAgent().getId().equals(agentId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Membre introuvable dans cette équipe"));

        member.setActive(false);
        memberRepository.save(member);

        addObservation(inv.getDossier(),
                ObservationType.INTERNAL_NOTE,
                "Membre retiré de l'équipe : " + member.getAgent().getNomComplet(),
                true, currentAgent);

        return investigationMapper.toResponse(
                getInvestigationOrThrow(investigationId));
    }

    // ── Helpers privés ────────────────────────────────────────

    private Investigation getInvestigationOrThrow(UUID id) {
        return investigationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Investigation introuvable : " + id));
    }

    private Agent getCurrentAgent() {
        String keycloakId = securityUtils.getCurrentKeycloakId()
                .orElseThrow(() -> new BusinessException("Agent non authentifié"));
        return agentRepository.findByKeycloakId(keycloakId)
                .orElseThrow(() -> new BusinessException(
                        "Agent introuvable. Contactez l'administrateur DDIC."));
    }

    private void addObservation(Dossier dossier, ObservationType type,
                                String content, boolean confidential,
                                Agent agent) {
        Observation obs = Observation.builder()
                .dossier(dossier)
                .type(type)
                .content(content)
                .confidential(confidential)
                .author(agent)
                .authorFullName(agent.getNomComplet())
                .statusSnapshot(dossier.getStatus())
                .build();
        observationRepository.save(obs);
    }

    private void recordDossierStatusChange(Dossier dossier,
                                           DossierStatus previous,
                                           DossierStatus next,
                                           String reason,
                                           Agent agent,
                                           String ipAddress) {
        StatusHistory history = StatusHistory.builder()
                .dossier(dossier)
                .previousStatus(previous)
                .newStatus(next)
                .reason(reason)
                .agent(agent)
                .agentFullName(agent.getNomComplet())
                .ipAddress(ipAddress)
                .build();
        statusHistoryRepository.save(history);
    }
}