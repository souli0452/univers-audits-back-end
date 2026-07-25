package gov.bf.ascelc.univers_audits.service.impl;

import gov.bf.ascelc.univers_audits.enums.*;
import gov.bf.ascelc.univers_audits.shared.exceptions.BusinessException;
import gov.bf.ascelc.univers_audits.shared.exceptions.ResourceNotFoundException;
import gov.bf.ascelc.univers_audits.mapper.InvestigationMapper;
import gov.bf.ascelc.univers_audits.model.dto.request.*;
import gov.bf.ascelc.univers_audits.model.dto.response.InvestigationResponse;
import gov.bf.ascelc.univers_audits.model.dto.response.InvestigationMemberResponse;
import gov.bf.ascelc.univers_audits.model.entity.*;
import gov.bf.ascelc.univers_audits.repository.*;
import gov.bf.ascelc.univers_audits.service.EmailService;
import gov.bf.ascelc.univers_audits.service.InvestigationService;
import gov.bf.ascelc.univers_audits.service.ParametreDelaiService;
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

        long memberCount = memberRepository
                .countByInvestigationIdAndActiveTrue(investigationId);

        if (memberCount == 0) {
            throw new BusinessException(
                    "L'équipe d'investigation doit avoir "
                            + "au moins un membre avant le démarrage");
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

        auditRecorder.addObservation(inv.getDossier(),
                ObservationType.INTERNAL_NOTE,
                "Membre retiré de l'équipe : " + member.getAgent().getNomComplet(),
                true, currentAgent);

        log.info("[removeMember] Membre retiré — agent: {}, investigation: {}",
                member.getAgent().getMatricule(), investigationId);

        return buildResponseWithFreshMembers(inv, investigationId);
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
        String  roleLabel     = TeamRole.TEAM_LEADER.equals(teamRole)
                ? "Chef de mission" : "Investigateur";

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
                    .subject("Vous avez été affecté(e) à l'investigation — "
                            + dossierNumber)
                    .content("Rôle : " + roleLabel
                            + " · Dossier : " + dossier.getObject())
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

    private Investigation getInvestigationOrThrow(UUID id) {
        return investigationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Investigation introuvable : " + id));
    }

}