package gov.bf.ascelc.univers_audits.service.impl;

import gov.bf.ascelc.univers_audits.enums.*;
import gov.bf.ascelc.univers_audits.shared.exceptions.BusinessException;
import gov.bf.ascelc.univers_audits.shared.exceptions.ResourceNotFoundException;
import gov.bf.ascelc.univers_audits.mapper.InvestigationMapper;
import gov.bf.ascelc.univers_audits.model.dto.request.*;
import gov.bf.ascelc.univers_audits.model.dto.response.InvestigationResponse;
import gov.bf.ascelc.univers_audits.model.dto.response.InvestigationMemberResponse;
import gov.bf.ascelc.univers_audits.model.dto.response.MandatResponse;
import gov.bf.ascelc.univers_audits.model.dto.response.EngagementConfidentialiteResponse;
import gov.bf.ascelc.univers_audits.model.dto.response.PlanInvestigationResponse;
import gov.bf.ascelc.univers_audits.model.dto.response.RevisionPlanResponse;
import gov.bf.ascelc.univers_audits.model.entity.*;
import gov.bf.ascelc.univers_audits.repository.*;
import gov.bf.ascelc.univers_audits.service.DossierHabilitationService;
import gov.bf.ascelc.univers_audits.service.EmailService;
import gov.bf.ascelc.univers_audits.service.InvestigationService;
import gov.bf.ascelc.univers_audits.service.ParametreDelaiService;
import gov.bf.ascelc.univers_audits.service.PortalConfigService;
import gov.bf.ascelc.univers_audits.shared.utils.AgentContextResolver;
import gov.bf.ascelc.univers_audits.shared.utils.DossierAuditRecorder;
import gov.bf.ascelc.univers_audits.shared.utils.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InvestigationServiceImpl implements InvestigationService {

    private final InvestigationRepository       investigationRepository;
    private final InvestigationMemberRepository memberRepository;
    private final DossierRepository             dossierRepository;
    private final AgentRepository               agentRepository;
    private final NotificationRepository        notificationRepository;
    private final EmailService                  emailService;
    private final InvestigationMapper           investigationMapper;
    private final SecurityUtils                 securityUtils;
    private final AgentContextResolver          agentContextResolver;
    private final DossierAuditRecorder          auditRecorder;
    private final ParametreDelaiService parametreDelaiService;
    private final DossierHabilitationService habilitationService;
    private final PortalConfigService portalConfigService;
    private final MandatRepository               mandatRepository;
    private final EngagementConfidentialiteRepository engagementConfidentialiteRepository;
    private final PlanInvestigationRepository     planInvestigationRepository;
    private final RevisionPlanRepository          revisionPlanRepository;

    @Value("${app.frontend.url:http://localhost:4200}")
    private String frontendUrl;


    // ════════════════════════════════════════════════════════════
    //  LECTURE
    // ════════════════════════════════════════════════════════════

    @Override
    public InvestigationResponse findById(UUID id) {
        Investigation inv = getInvestigationOrThrow(id);
        return buildResponseWithFreshMembers(inv, id);
    }

    @Override
    public InvestigationResponse findByDossierId(UUID dossierId) {
        Investigation inv = investigationRepository
                .findByDossierId(dossierId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Aucune investigation pour ce dossier"));
        return buildResponseWithFreshMembers(inv, inv.getId());
    }

    @Override
    public Page<InvestigationResponse> findAll(Pageable pageable) {
        return mapPageWithMembers(investigationRepository.findAll(pageable));
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

    @Override
    @Transactional(readOnly = true)
    public Page<InvestigationResponse> findByPeriod(
            Instant start, Instant end, Pageable pageable) {
        return mapPageWithMembers(
                investigationRepository.findByStartDateBetween(start, end, pageable));
    }

    /**
     * Recharge les membres (JOIN FETCH) pour les seuls investigations d'une
     * page déjà paginée au niveau SQL — évite le N+1 (un appel par ligne)
     * sans jamais combiner JOIN FETCH sur une collection avec Pageable
     * (ce qui forcerait Hibernate à paginer en mémoire, voir InvestigationRepository).
     */
    private Page<InvestigationResponse> mapPageWithMembers(Page<Investigation> page) {
        List<UUID> ids = page.getContent().stream().map(Investigation::getId).toList();
        if (ids.isEmpty()) {
            return page.map(investigationMapper::toResponse);
        }

        Map<UUID, Investigation> withMembers = investigationRepository
                .findAllWithMembersByIdIn(ids).stream()
                .collect(Collectors.toMap(Investigation::getId, i -> i));

        List<InvestigationResponse> content = page.getContent().stream()
                .map(i -> investigationMapper.toResponse(
                        withMembers.getOrDefault(i.getId(), i)))
                .toList();

        return new PageImpl<>(content, page.getPageable(), page.getTotalElements());
    }


    // ════════════════════════════════════════════════════════════
    //  CYCLE DE VIE INVESTIGATION
    // ════════════════════════════════════════════════════════════

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

        Agent cgea = agentContextResolver.getCurrentAgent();

        Investigation investigation = Investigation.builder()
                .dossier(dossier)
                .cgea(cgea)
                .status(InvestigationStatus.INITIATED)
                .plannedDurationDays(
                        request.getPlannedDurationDays() != null
                                ? request.getPlannedDurationDays()
                                : parametreDelaiService.resolveDelaiJours(
                                        "INVESTIGATION_DUREE_DEFAUT"))
                .build();

        Investigation saved = investigationRepository.save(investigation);

        dossier.setStatus(DossierStatus.EN_INVESTIGATION);
        dossierRepository.save(dossier);

        auditRecorder.recordStatusChange(dossier,
                DossierStatus.RECEVABLE,
                DossierStatus.EN_INVESTIGATION,
                "Investigation ouverte par le CGEA",
                cgea, ipAddress);

        auditRecorder.addObservation(dossier,
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

        validateTeamComposition(investigationId);

        if (mandatRepository.findByInvestigationId(investigationId).isEmpty()) {
            throw new BusinessException(
                    "Aucun mandat n'a été délivré par le CGE pour cette investigation.");
        }

        inv.start();
        Investigation saved = investigationRepository.save(inv);

        auditRecorder.addObservation(inv.getDossier(),
                ObservationType.INTERNAL_NOTE,
                "Investigation démarrée. Date de fin prévue : "
                        + saved.getPlannedEndDate(),
                true, agentContextResolver.getCurrentAgent());

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

        auditRecorder.addObservation(inv.getDossier(),
                ObservationType.INTERNAL_NOTE,
                "Investigation suspendue. Motif : " + reason,
                true, agentContextResolver.getCurrentAgent());

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

        auditRecorder.addObservation(inv.getDossier(),
                ObservationType.INTERNAL_NOTE,
                "Investigation reprise. " + (reason != null ? reason : ""),
                true, agentContextResolver.getCurrentAgent());

        return investigationMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public InvestigationResponse extendDeadline(
            UUID investigationId,
            ExtendDeadlineRequest request,
            String ipAddress) {

        Investigation inv = getInvestigationOrThrow(investigationId);
        Agent cgea = agentContextResolver.getCurrentAgent();

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

        auditRecorder.addObservation(inv.getDossier(),
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

        auditRecorder.recordStatusChange(dossier,
                DossierStatus.EN_INVESTIGATION,
                DossierStatus.RAPPORT_PRODUIT,
                "Rapport d'investigation soumis",
                agentContextResolver.getCurrentAgent(), ipAddress);

        Investigation saved = investigationRepository.save(inv);

        auditRecorder.addObservation(dossier,
                ObservationType.FIELD_FINDING,
                "Rapport final soumis. Conclusions : " + request.getConclusions(),
                true, agentContextResolver.getCurrentAgent());

        log.info("Rapport soumis — investigation: {}", investigationId);
        return investigationMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public InvestigationResponse approveDei(UUID investigationId, String ipAddress) {
        Investigation inv = getInvestigationOrThrow(investigationId);

        if (inv.getStatus() != InvestigationStatus.COMPLETED) {
            throw new BusinessException(
                    "L'approbation DEI n'est possible qu'après soumission du rapport.");
        }

        Agent agent = agentContextResolver.getCurrentAgent();
        inv.setDeiApprovedAt(Instant.now());
        inv.setDeiApprovedBy(agent);
        Investigation saved = investigationRepository.save(inv);

        auditRecorder.addObservation(inv.getDossier(),
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

        Agent agent = agentContextResolver.getCurrentAgent();
        inv.setLegalAdvisorApprovedAt(Instant.now());
        inv.setLegalAdvisorApprovedBy(agent);
        Investigation saved = investigationRepository.save(inv);

        auditRecorder.addObservation(inv.getDossier(),
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

        Agent cge = agentContextResolver.getCurrentAgent();
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
        } else {
            newDossierStatus   = DossierStatus.DECISION_RENDUE;
            transitionReason   = "Décision finale CGE rendue. Outcome : "
                    + inv.getOutcome() + ". " + reason;
            observationContent = "Décision finale rendue par le CGE. Outcome : "
                    + inv.getOutcome().name() + ". " + reason;
        }

        dossier.setStatus(newDossierStatus);
        dossierRepository.save(dossier);
        auditRecorder.recordStatusChange(dossier, previousStatus, newDossierStatus,
                transitionReason, cge, ipAddress);

        Investigation saved = investigationRepository.save(inv);
        auditRecorder.addObservation(dossier, ObservationType.CGE_DECISION,
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
                    "Cet agent est déjà membre actif de cette investigation");
        }

        Agent agent = agentRepository.findById(request.getAgentId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Agent introuvable : " + request.getAgentId()));

        EngagementConfidentialite engagement = engagementConfidentialiteRepository
                .findByInvestigationIdAndAgentId(investigationId, request.getAgentId())
                .orElseThrow(() -> new BusinessException(
                        "L'agent doit d'abord déclarer l'absence de conflit d'intérêts et "
                                + "signer l'engagement de confidentialité avant d'être affecté "
                                + "à l'équipe."));

        if (Boolean.TRUE.equals(engagement.getHasConflictOfInterest())) {
            throw new BusinessException(
                    "Cet agent a déclaré un conflit d'intérêts et ne peut pas être affecté "
                            + "à cette investigation.");
        }

        Agent currentAgent = agentContextResolver.getCurrentAgent();

        Optional<InvestigationMember> existing =
                memberRepository.findFirstByInvestigationIdAndAgentIdOrderByCreatedAtDesc(
                        investigationId, request.getAgentId());

        if (existing.isPresent()) {
            InvestigationMember member = existing.get();
            member.setActive(true);
            member.setTeamRole(request.getTeamRole());
            member.setAssignedBy(currentAgent.getKeycloakId());
            memberRepository.save(member);
            log.info("[addMember] Membre réactivé — agent: {}", agent.getMatricule());

        } else {
            InvestigationMember member = InvestigationMember.builder()
                    .investigation(inv)
                    .agent(agent)
                    .teamRole(request.getTeamRole())
                    .assignedBy(currentAgent.getKeycloakId())
                    .active(true)
                    .build();

            InvestigationMember saved = memberRepository.save(member);

            inv.getMembers().add(saved);

            log.info("[addMember] Nouveau membre ajouté — agent: {}", agent.getMatricule());
        }

        habilitationService.grant(inv.getDossier(), agent, HabilitationSource.INVESTIGATION_TEAM,
                currentAgent, "Membre de l'équipe d'investigation");

        auditRecorder.addObservation(inv.getDossier(),
                ObservationType.INTERNAL_NOTE,
                "Membre ajouté à l'équipe : "
                        + agent.getNomComplet()
                        + " (" + request.getTeamRole() + ")",
                true, currentAgent);

        sendMemberAddedNotifications(inv, agent, request.getTeamRole());

        return buildResponseWithFreshMembers(inv, investigationId);
    }

    @Override
    @Transactional
    public InvestigationResponse removeMember(
            UUID investigationId,
            UUID agentId,
            String ipAddress) {

        Investigation inv = getInvestigationOrThrow(investigationId);
        Agent currentAgent = agentContextResolver.getCurrentAgent();

        InvestigationMember member = inv.getMembers().stream()
                .filter(m -> Boolean.TRUE.equals(m.getActive())
                        && m.getAgent().getId().equals(agentId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Membre introuvable dans cette équipe"));
        member.setActive(false);
        memberRepository.save(member);

        habilitationService.revokeBySource(inv.getDossier(), member.getAgent(),
                HabilitationSource.INVESTIGATION_TEAM, currentAgent);

        auditRecorder.addObservation(inv.getDossier(),
                ObservationType.INTERNAL_NOTE,
                "Membre retiré de l'équipe : " + member.getAgent().getNomComplet(),
                true, currentAgent);

        log.info("[removeMember] Membre retiré — agent: {}, investigation: {}",
                member.getAgent().getMatricule(), investigationId);

        return buildResponseWithFreshMembers(inv, investigationId);
    }

    @Override
    @Transactional
    public EngagementConfidentialiteResponse declareEngagementPrealable(
            UUID investigationId,
            EngagementConfidentialiteRequest request,
            String ipAddress) {

        Investigation inv = getInvestigationOrThrow(investigationId);
        Agent agent = agentContextResolver.getCurrentAgent();

        if (Boolean.TRUE.equals(request.getHasConflictOfInterest())
                && (request.getConflictDetails() == null
                        || request.getConflictDetails().isBlank())) {
            throw new BusinessException(
                    "Veuillez préciser la nature du conflit d'intérêts déclaré.");
        }

        if (engagementConfidentialiteRepository
                .findByInvestigationIdAndAgentId(investigationId, agent.getId())
                .isPresent()) {
            throw new BusinessException(
                    "Une déclaration a déjà été soumise pour cet agent sur cette investigation.");
        }

        EngagementConfidentialite engagement = EngagementConfidentialite.builder()
                .investigation(inv)
                .agent(agent)
                .hasConflictOfInterest(request.getHasConflictOfInterest())
                .conflictDetails(request.getConflictDetails())
                .signedAt(Instant.now())
                .build();
        EngagementConfidentialite saved = engagementConfidentialiteRepository.save(engagement);

        log.info("Engagement de confidentialité signé — investigation: {}, agent: {}",
                investigationId, agent.getId());
        return toEngagementConfidentialiteResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public EngagementConfidentialiteResponse getEngagementPrealable(
            UUID investigationId, UUID agentId) {
        EngagementConfidentialite engagement = engagementConfidentialiteRepository
                .findByInvestigationIdAndAgentId(investigationId, agentId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Aucun engagement de confidentialité pour cet agent sur cette "
                                + "investigation."));
        return toEngagementConfidentialiteResponse(engagement);
    }

    @Override
    @Transactional
    public MandatResponse deliverMandat(UUID investigationId, String ipAddress) {

        Investigation inv = getInvestigationOrThrow(investigationId);

        if (mandatRepository.findByInvestigationId(investigationId).isPresent()) {
            throw new BusinessException(
                    "Un mandat a déjà été délivré pour cette investigation.");
        }

        validateTeamComposition(investigationId);

        Agent cge = agentContextResolver.getCurrentAgent();
        Mandat mandat = Mandat.builder()
                .investigation(inv)
                .dateDelivrance(Instant.now())
                .agentCGE(cge)
                .build();
        Mandat saved = mandatRepository.save(mandat);

        auditRecorder.addObservation(inv.getDossier(),
                ObservationType.INTERNAL_NOTE,
                "Mandat délivré par le CGE — signataire : " + cge.getNomComplet(),
                true, cge);

        log.info("Mandat délivré — investigation: {}", investigationId);
        return toMandatResponse(saved);
    }

    @Override
    public MandatResponse getMandat(UUID investigationId) {
        Mandat mandat = mandatRepository.findByInvestigationId(investigationId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Aucun mandat pour cette investigation : " + investigationId));
        return toMandatResponse(mandat);
    }

    @Override
    @Transactional
    public PlanInvestigationResponse submitPlan(
            UUID investigationId,
            PlanInvestigationSubmitRequest request,
            String ipAddress) {

        Investigation inv = getInvestigationOrThrow(investigationId);

        if (mandatRepository.findByInvestigationId(investigationId).isEmpty()) {
            throw new BusinessException(
                    "Aucun mandat n'a été délivré par le CGE pour cette investigation.");
        }

        if (planInvestigationRepository.findByInvestigationId(investigationId).isPresent()) {
            throw new BusinessException(
                    "Un plan d'investigation existe déjà pour cette investigation. "
                            + "Utilisez la révision pour le modifier.");
        }

        Agent currentAgent = agentContextResolver.getCurrentAgent();

        PlanInvestigation plan = PlanInvestigation.builder()
                .investigation(inv)
                .objectifs(request.getObjectifs())
                .methodologie(request.getMethodologie())
                .moyensMobilises(request.getMoyensMobilises())
                .planningProcedures(request.getPlanningProcedures())
                .planVersion(1)
                .submittedAt(Instant.now())
                .submittedBy(currentAgent)
                .build();
        PlanInvestigation saved = planInvestigationRepository.save(plan);

        auditRecorder.addObservation(inv.getDossier(),
                ObservationType.INTERNAL_NOTE,
                "Plan d'investigation soumis par " + currentAgent.getNomComplet(),
                true, currentAgent);

        log.info("Plan d'investigation soumis — investigation: {}", investigationId);
        return toPlanInvestigationResponse(saved);
    }

    @Override
    @Transactional
    public PlanInvestigationResponse revisePlan(
            UUID investigationId,
            PlanInvestigationRevisionRequest request,
            String ipAddress) {

        Investigation inv = getInvestigationOrThrow(investigationId);

        PlanInvestigation plan = planInvestigationRepository
                .findByInvestigationId(investigationId)
                .orElseThrow(() -> new BusinessException(
                        "Aucun plan d'investigation n'existe pour cette investigation. "
                                + "Utilisez la soumission initiale."));

        Agent currentAgent = agentContextResolver.getCurrentAgent();

        RevisionPlan revision = RevisionPlan.builder()
                .planInvestigation(plan)
                .versionNumber(plan.getPlanVersion())
                .objectifs(plan.getObjectifs())
                .methodologie(plan.getMethodologie())
                .moyensMobilises(plan.getMoyensMobilises())
                .planningProcedures(plan.getPlanningProcedures())
                .revisedAt(Instant.now())
                .revisedBy(currentAgent)
                .motifRevision(request.getMotifRevision())
                .build();
        revisionPlanRepository.save(revision);

        plan.setObjectifs(request.getObjectifs());
        plan.setMethodologie(request.getMethodologie());
        plan.setMoyensMobilises(request.getMoyensMobilises());
        plan.setPlanningProcedures(request.getPlanningProcedures());
        plan.setPlanVersion(plan.getPlanVersion() + 1);
        plan.setValidatedAt(null);
        plan.setValidatedBy(null);
        PlanInvestigation saved = planInvestigationRepository.save(plan);

        auditRecorder.addObservation(inv.getDossier(),
                ObservationType.INTERNAL_NOTE,
                "Plan d'investigation révisé par " + currentAgent.getNomComplet()
                        + " — motif : " + request.getMotifRevision(),
                true, currentAgent);

        log.info("Plan d'investigation révisé — investigation: {}, nouvelle version: {}",
                investigationId, saved.getPlanVersion());
        return toPlanInvestigationResponse(saved);
    }

    @Override
    @Transactional
    public PlanInvestigationResponse validatePlan(UUID investigationId, String ipAddress) {

        Investigation inv = getInvestigationOrThrow(investigationId);

        PlanInvestigation plan = planInvestigationRepository
                .findByInvestigationId(investigationId)
                .orElseThrow(() -> new BusinessException(
                        "Aucun plan d'investigation n'existe pour cette investigation."));

        if (plan.getValidatedAt() != null) {
            throw new BusinessException(
                    "Ce plan d'investigation a déjà été validé.");
        }

        Agent currentAgent = agentContextResolver.getCurrentAgent();
        plan.setValidatedAt(Instant.now());
        plan.setValidatedBy(currentAgent);
        PlanInvestigation saved = planInvestigationRepository.save(plan);

        auditRecorder.addObservation(inv.getDossier(),
                ObservationType.INTERNAL_NOTE,
                "Plan d'investigation validé par le DEI — " + currentAgent.getNomComplet(),
                true, currentAgent);

        log.info("Plan d'investigation validé — investigation: {}", investigationId);
        return toPlanInvestigationResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public PlanInvestigationResponse getPlan(UUID investigationId) {
        PlanInvestigation plan = planInvestigationRepository
                .findByInvestigationId(investigationId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Aucun plan d'investigation pour cette investigation : "
                                + investigationId));
        return toPlanInvestigationResponse(plan);
    }

    @Override
    @Transactional(readOnly = true)
    public List<RevisionPlanResponse> getPlanRevisions(UUID investigationId) {
        PlanInvestigation plan = planInvestigationRepository
                .findByInvestigationId(investigationId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Aucun plan d'investigation pour cette investigation : "
                                + investigationId));

        return revisionPlanRepository
                .findByPlanInvestigationIdOrderByVersionNumberDesc(plan.getId())
                .stream()
                .map(this::toRevisionPlanResponse)
                .toList();
    }


    private InvestigationResponse buildResponseWithFreshMembers(
            Investigation inv, UUID investigationId) {

        InvestigationResponse response = investigationMapper.toResponse(inv);

        List<InvestigationMember> freshMembers =
                memberRepository.findByInvestigationIdAndActiveTrue(investigationId);

        List<InvestigationMemberResponse> memberResponses = freshMembers.stream()
                .map(investigationMapper::toMemberResponse)
                .toList();

        response.setMembers(memberResponses);
        response.setMemberCount(memberResponses.size());

        return response;
    }


    private void sendMemberAddedNotifications(Investigation inv,
                                              Agent agent,
                                              TeamRole teamRole) {
        Dossier dossier       = inv.getDossier();
        String  dossierNumber = dossier.getNumber() != null
                ? dossier.getNumber() : "(en attente de numéro)";
        String roleLabel = switch (teamRole) {
            case CHEF_MISSION -> "Chef de mission";
            case INVESTIGATEUR -> "Investigateur";
            case PERSONNE_RESSOURCE -> "Personne ressource";
            case CONSEIL_JURIDIQUE -> "Conseil juridique";
        };

        String linkDossier       = frontendUrl + "/#/app/dossiers/"
                + dossier.getId().toString();
        String linkInvestigation = frontendUrl + "/#/app/investigations/"
                + inv.getId().toString();

        if (agent.getEmail() != null && !agent.getEmail().isBlank()) {
            try {
                emailService.sendInvestigationAssignment(
                        agent.getEmail(),
                        agent.getNomComplet(),
                        dossierNumber,
                        dossier.getObject(),
                        roleLabel,
                        linkDossier,
                        linkInvestigation
                );
                log.info("[addMember] Email affectation envoyé → {} ({})",
                        agent.getEmail(), dossierNumber);
            } catch (Exception e) {
                log.error("[addMember] Échec email affectation agent={} : {}",
                        agent.getMatricule(), e.getMessage());
            }
        } else {
            log.warn("[addMember] Agent {} sans email — notification email ignorée",
                    agent.getMatricule());
        }

        try {
            Notification notif = Notification.builder()
                    .dossier(dossier)
                    .type(NotificationType.INVESTIGATION_ASSIGNMENT)
                    .channel(NotificationChannel.PORTAL)
                    .recipient(agent.getKeycloakId())
                    .subject(portalConfigService.resolveNotificationText(
                            "notif_subject_investigation_assignment",
                            Map.of("numero", dossierNumber)))
                    .content(portalConfigService.resolveNotificationText(
                            "notif_content_investigation_assignment",
                            Map.of("role", roleLabel != null ? roleLabel : "",
                                    "objet", dossier.getObject() != null ? dossier.getObject() : "")))
                    .scheduledAt(Instant.now())
                    .build();

            notificationRepository.save(notif);
            log.info("[addMember] Notification portail créée pour agent={}",
                    agent.getMatricule());
        } catch (Exception e) {
            log.error("[addMember] Échec notification portail agent={} : {}",
                    agent.getMatricule(), e.getMessage());
        }
    }


    // ════════════════════════════════════════════════════════════
    //  MÉTHODES PRIVÉES
    // ════════════════════════════════════════════════════════════

    private void validateTeamComposition(UUID investigationId) {
        long chefMission = memberRepository.countByInvestigationIdAndTeamRoleAndActiveTrue(
                investigationId, TeamRole.CHEF_MISSION);
        if (chefMission != 1) {
            throw new BusinessException(
                    "L'équipe doit compter exactement un chef de mission (trouvé : "
                            + chefMission + ").");
        }

        long investigateurs = memberRepository.countByInvestigationIdAndTeamRoleAndActiveTrue(
                investigationId, TeamRole.INVESTIGATEUR);
        if (investigateurs < 2) {
            throw new BusinessException(
                    "L'équipe doit compter au moins deux investigateurs (trouvé : "
                            + investigateurs + ").");
        }

        long conseilJuridique = memberRepository.countByInvestigationIdAndTeamRoleAndActiveTrue(
                investigationId, TeamRole.CONSEIL_JURIDIQUE);
        if (conseilJuridique != 1) {
            throw new BusinessException(
                    "L'équipe doit compter exactement un conseil juridique (trouvé : "
                            + conseilJuridique + ").");
        }
    }

    private MandatResponse toMandatResponse(Mandat mandat) {
        return MandatResponse.builder()
                .id(mandat.getId())
                .investigationId(mandat.getInvestigation().getId())
                .dateDelivrance(mandat.getDateDelivrance())
                .agentCGEId(mandat.getAgentCGE().getId())
                .agentCGENom(mandat.getAgentCGE().getNomComplet())
                .build();
    }

    private EngagementConfidentialiteResponse toEngagementConfidentialiteResponse(
            EngagementConfidentialite engagement) {
        return EngagementConfidentialiteResponse.builder()
                .id(engagement.getId())
                .investigationId(engagement.getInvestigation().getId())
                .agentId(engagement.getAgent().getId())
                .agentNom(engagement.getAgent().getNomComplet())
                .hasConflictOfInterest(engagement.getHasConflictOfInterest())
                .conflictDetails(engagement.getConflictDetails())
                .signedAt(engagement.getSignedAt())
                .build();
    }

    private PlanInvestigationResponse toPlanInvestigationResponse(PlanInvestigation plan) {
        UUID investigationId = plan.getInvestigation().getId();

        Instant validationDeadline = null;
        Optional<Instant> dateDelivrance = mandatRepository
                .findByInvestigationId(investigationId)
                .map(Mandat::getDateDelivrance);
        if (dateDelivrance.isPresent()) {
            try {
                int delaiJours = parametreDelaiService.resolveDelaiJours(
                        "VALIDATION_PLAN_INVESTIGATION_DEI");
                validationDeadline = dateDelivrance.get().plusSeconds((long) delaiJours * 24 * 3600);
            } catch (ResourceNotFoundException e) {
                log.warn("Délai VALIDATION_PLAN_INVESTIGATION_DEI indisponible — "
                        + "échéance de validation non calculée : {}", e.getMessage());
            }
        }

        boolean overdue = validationDeadline != null
                && plan.getValidatedAt() == null
                && Instant.now().isAfter(validationDeadline);

        return PlanInvestigationResponse.builder()
                .id(plan.getId())
                .investigationId(investigationId)
                .planVersion(plan.getPlanVersion())
                .objectifs(plan.getObjectifs())
                .methodologie(plan.getMethodologie())
                .moyensMobilises(plan.getMoyensMobilises())
                .planningProcedures(plan.getPlanningProcedures())
                .submittedAt(plan.getSubmittedAt())
                .submittedById(plan.getSubmittedBy().getId())
                .submittedByNom(plan.getSubmittedBy().getNomComplet())
                .validatedAt(plan.getValidatedAt())
                .validatedById(plan.getValidatedBy() != null
                        ? plan.getValidatedBy().getId() : null)
                .validatedByNom(plan.getValidatedBy() != null
                        ? plan.getValidatedBy().getNomComplet() : null)
                .validationDeadline(validationDeadline)
                .overdue(overdue)
                .build();
    }

    private RevisionPlanResponse toRevisionPlanResponse(RevisionPlan revision) {
        return RevisionPlanResponse.builder()
                .id(revision.getId())
                .versionNumber(revision.getVersionNumber())
                .objectifs(revision.getObjectifs())
                .methodologie(revision.getMethodologie())
                .moyensMobilises(revision.getMoyensMobilises())
                .planningProcedures(revision.getPlanningProcedures())
                .revisedAt(revision.getRevisedAt())
                .revisedById(revision.getRevisedBy().getId())
                .revisedByNom(revision.getRevisedBy().getNomComplet())
                .motifRevision(revision.getMotifRevision())
                .build();
    }

    private Investigation getInvestigationOrThrow(UUID id) {
        return investigationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Investigation introuvable : " + id));
    }

}